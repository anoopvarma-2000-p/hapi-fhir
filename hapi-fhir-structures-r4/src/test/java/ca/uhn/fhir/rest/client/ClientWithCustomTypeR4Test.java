package ca.uhn.fhir.rest.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientWithCustomTypeR4Test {
  private static FhirContext ourCtx;
  private HttpClient myHttpClient;
  private HttpResponse myHttpResponse;

  @AfterAll
  public static void afterClassClearContext() {
    TestUtil.randomizeLocaleAndTimezone();
  }

  @BeforeEach
  public void before() {
    myHttpClient = mock(HttpClient.class, new ReturnsDeepStubs());
    ourCtx.getRestfulClientFactory().setHttpClient(myHttpClient);
    ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    myHttpResponse = mock(HttpResponse.class, new ReturnsDeepStubs());
  }

  @Test
  public void testReadCustomType() throws Exception {
    IParser p = ourCtx.newXmlParser();

    MyPatientWithExtensions response = new MyPatientWithExtensions();
    response.addName().setFamily("FAMILY");
    response.getStringExt().setValue("STRINGVAL");
    response.getDateExt().setValueAsString("2011-01-02");
    final String respString = p.encodeResourceToString(response);

    ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
    when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
    when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
    when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
    when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
      @Override
      public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
        return new ReaderInputStream(new StringReader(respString), Charset.forName("UTF-8"));
      }
    });

    IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

    //@formatter:off
		MyPatientWithExtensions value = client
			.read()
			.resource(MyPatientWithExtensions.class)
			.withId("123")
			.execute();
		//@formatter:on

    HttpUriRequest request = capt.getAllValues().get(0);

		assertThat(request.getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123");
		assertThat(request.getMethod()).isEqualTo("GET");

		assertThat(value.getName()).hasSize(1);
		assertThat(value.getName().get(0).getFamily()).isEqualTo("FAMILY");
		assertThat(value.getStringExt().getValue()).isEqualTo("STRINGVAL");
		assertThat(value.getDateExt().getValueAsString()).isEqualTo("2011-01-02");

  }

  @Test
  public void testSearchWithGenericReturnType() throws Exception {

    final Bundle bundle = new Bundle();

    final ExtendedPatient patient = new ExtendedPatient();
    patient.addIdentifier().setValue("PRP1660");
    bundle.addEntry().setResource(patient);

    final Organization org = new Organization();
    org.setName("FOO");
    patient.getManagingOrganization().setResource(org);

    final FhirContext ctx = FhirContext.forR4();
    ctx.setDefaultTypeForProfile(ExtendedPatient.HTTP_FOO_PROFILES_PROFILE, ExtendedPatient.class);
    ctx.getRestfulClientFactory().setHttpClient(myHttpClient);
    ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

    String msg = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);

    ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

    when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
    when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
    when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

    // httpResponse = new BasicHttpResponse(statusline, catalog, locale)
    when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

    ITestClient client = ctx.newRestfulClient(ITestClient.class, "http://foo");
    List<IBaseResource> response = client.getPatientByDobWithGenericResourceReturnType(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");
    ExtendedPatient patientResp = (ExtendedPatient) response.get(0);
		assertThat(patientResp.getIdentifier().get(0).getValue()).isEqualTo("PRP1660");

  }

  @Test
  public void testSearchWithGenericReturnType2() throws Exception {

    final Bundle bundle = new Bundle();

    final ExtendedPatient patient = new ExtendedPatient();
    patient.addIdentifier().setValue("PRP1660");
    bundle.addEntry().setResource(patient);

    final Organization org = new Organization();
    org.setName("FOO");
    patient.getManagingOrganization().setResource(org);

    final FhirContext ctx = FhirContext.forR4();
    ctx.setDefaultTypeForProfile(ExtendedPatient.HTTP_FOO_PROFILES_PROFILE, ExtendedPatient.class);
    ctx.getRestfulClientFactory().setHttpClient(myHttpClient);
    ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

    String msg = ctx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);

    ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

    when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
    when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
    when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

    // httpResponse = new BasicHttpResponse(statusline, catalog, locale)
    when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

    ITestClient client = ctx.newRestfulClient(ITestClient.class, "http://foo");
    List<IAnyResource> response = client.getPatientByDobWithGenericResourceReturnType2(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");
    ExtendedPatient patientResp = (ExtendedPatient) response.get(0);
		assertThat(patientResp.getIdentifier().get(0).getValue()).isEqualTo("PRP1660");

  }

  @BeforeAll
  public static void beforeClass() {
    ourCtx = FhirContext.forR4();
  }

}
