package ca.uhn.fhir.rest.client;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.rest.annotation.At;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Elements;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.apache.ApacheHttpRequest;
import ca.uhn.fhir.rest.client.apache.ResourceEntity;
import ca.uhn.fhir.rest.client.api.IBasicClient;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.client.interceptor.CapturingInterceptor;
import ca.uhn.fhir.rest.param.CompositeParam;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.param.StringOrListParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import ca.uhn.fhir.util.BundleBuilder;
import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.util.UrlUtil;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hamcrest.core.StringContains;
import org.hamcrest.core.StringEndsWith;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.assertj.core.api.Assertions.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientR4Test {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ClientR4Test.class);
	private static FhirContext ourCtx = FhirContext.forR4();
	private HttpClient myHttpClient;

	private HttpResponse myHttpResponse;

	// atom-document-large.xml

	@BeforeEach
	public void before() {

		myHttpClient = mock(HttpClient.class, new ReturnsDeepStubs());
		ourCtx.getRestfulClientFactory().setHttpClient(myHttpClient);
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

		myHttpResponse = mock(HttpResponse.class, new ReturnsDeepStubs());
	}

	public String getHistoryBundleWithTwoResults() {
    /*
	   *       //@formatter:off
      String msg = "<feed xmlns=\"http://www.w3.org/2005/Atom\"><title/><id>6c1d93be-027f-468d-9d47-f826cd15cf42</id>"
            + "<link rel=\"self\" href=\"http://localhost:51698/Patient/222/_history\"/>"
            + "<link rel=\"fhir-base\" href=\"http://localhost:51698\"/><os:totalResults xmlns:os=\"http://a9.com/-/spec/opensearch/1.1/\">2</os:totalResults>"
            + "<author><name>ca.uhn.fhir.rest.method.HistoryMethodBinding</name></author>"
            + "<entry><title>Patient 222</title><id>222</id>"
            + "<updated>"+date1.getValueAsString()+"</updated>"
            + "<published>"+date2.getValueAsString()+"</published>"
            + "<link rel=\"self\" href=\"http://localhost:51698/Patient/222/_history/1\"/>"
            + "<content type=\"text/xml\"><Patient xmlns=\"http://hl7.org/fhir\"><identifier><use value=\"official\"/><system value=\"urn:hapitest:mrns\"/><value value=\"00001\"/></identifier><name><family value=\"OlderFamily\"/><given value=\"PatientOne\"/></name><gender><text value=\"M\"/></gender></Patient></content>"
            + "</entry>"
            + "<entry><title>Patient 222</title><id>222</id>"
            + "<updated>"+date3.getValueAsString()+"</updated>"
            + "<published>"+date4.getValueAsString()+"</published>"
            + "<link rel=\"self\" href=\"http://localhost:51698/Patient/222/_history/2\"/><content type=\"text/xml\"><Patient xmlns=\"http://hl7.org/fhir\"><identifier><use value=\"official\"/><system value=\"urn:hapitest:mrns\"/><value value=\"00001\"/></identifier><name><family value=\"NewerFamily\"/><given value=\"PatientOne\"/></name><gender><text value=\"M\"/></gender></Patient></content></entry></feed>";
      //@formatter:on
	   */

		Bundle retVal = new Bundle();

		Patient p1 = new Patient();
		p1.addName().setFamily("OldeerFamily").addGiven("PatientOne");
		retVal
			.addEntry()
			.setFullUrl("http://acme.com/Patient/111")
			.setResource(p1);

		Patient p2 = new Patient();
		p2.addName().setFamily("NewerFamily").addGiven("PatientOne");
		retVal
			.addEntry()
			.setFullUrl("http://acme.com/Patient/222")
			.setResource(p2);

		return ourCtx.newXmlParser().encodeResourceToString(retVal);

	}

	private String getPatient() {
		Patient p = new Patient();
		p.getMeta().getLastUpdatedElement().setValueAsString("1995-11-15T03:58:08.000-01:00");
		p
			.getMeta()
			.addTag()
			.setSystem("http://hl7.org/fhir/tag")
			.setCode("http://foo/tagdefinition.html")
			.setDisplay("Some tag");

		p.setId("http://foo.com/Patient/123/_history/2333");
		p.addName().setFamily("Kramer").addGiven("Doe");
		p.addIdentifier().setValue("PRP1660");
		String msg = EncodingEnum.XML.newParser(ourCtx).setPrettyPrint(true).encodeResourceToString(p);
		return msg;
	}

	@Test
	public void testCreate() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		CapturingInterceptor interceptor = new CapturingInterceptor();
		client.registerInterceptor(interceptor);

		MethodOutcome response = client.createPatient(patient);

		assertThat("http://foo/Patient").isEqualTo(((ApacheHttpRequest) interceptor.getLastRequest()).getApacheRequest().getURI().toASCIIString());

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPost.class);
		HttpPost post = (HttpPost) capt.getValue();
		assertThat(IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8), StringContains.containsString("{\"resourceType\":\"Patient\""));
		assertThat(response.getId().getValue()).isEqualTo("http://example.com/fhir/Patient/100/_history/200");
		assertThat(capt.getAllValues().get(0).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.JSON.getResourceContentTypeNonLegacy() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(response.getId().getVersionIdPart()).isEqualTo("200");
	}

	@Test
	public void testCreateBad() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 400, "foobar"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader("foobar"), StandardCharsets.UTF_8));

		try {
			ourCtx.newRestfulClient(ITestClient.class, "http://foo").createPatient(patient);
			fail("");		} catch (InvalidRequestException e) {
			assertThat(e.getMessage(), StringContains.containsString("foobar"));
		}
	}

	/**
	 * See #2297
	 */
	@Test
	public void testCreateBundlePreservesIds() throws Exception {

		BundleBuilder bb = new BundleBuilder(ourCtx);
		bb.setType("collection");

		Patient patient = new Patient();
		patient.setId("Patient/123");
		patient.addIdentifier().setSystem("urn:foo").setValue("bar");
		bb.addCollectionEntry(patient);

		IBaseBundle inputBundle = bb.getBundle();
		inputBundle.setId("ABC");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://foo");
		client.setEncoding(EncodingEnum.JSON);
		CapturingInterceptor interceptor = new CapturingInterceptor();
		client.registerInterceptor(interceptor);

		client.create().resource(inputBundle).execute();

		assertThat(((ApacheHttpRequest) interceptor.getLastRequest()).getApacheRequest().getURI().toASCIIString()).isEqualTo("http://foo/Bundle?_format=json");

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPost.class);
		HttpPost post = (HttpPost) capt.getValue();
		String requestBody = IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8);
		ourLog.info("Request body: {}", requestBody);
		assertThat(requestBody, StringContains.containsString("{\"resourceType\":\"Patient\""));
		Bundle requestBundle = ourCtx.newJsonParser().parseResource(Bundle.class, requestBody);

		assertThat(requestBundle.getEntry().get(0).getResource().getIdElement().getIdPart()).isEqualTo("123");
		assertThat(requestBody).contains("\"id\":\"123\"");
		assertThat(requestBody).doesNotContain("\"id\":\"ABC\"");
	}

	/**
	 * Some servers (older ones?) return the resourcde you created instead of an OperationOutcome. We just need to ignore
	 * it.
	 */
	@Test
	public void testCreateWithResourceResponse() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(ourCtx.newXmlParser().encodeResourceToString(patient)), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome response = client.createPatient(patient);

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPost.class);
		HttpPost post = (HttpPost) capt.getValue();
		assertThat(IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8), StringContains.containsString("\"Patient"));
		assertThat(response.getId().getValue()).isEqualTo("http://example.com/fhir/Patient/100/_history/200");
		assertThat(response.getId().getVersionIdPart()).isEqualTo("200");
	}

	@Test
	public void testStringIncludeTest() throws Exception {

		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.SEARCHSET);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(ourCtx.newXmlParser().encodeResourceToString(bundle)), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(new Header[0]);

		MyClient client = ourCtx.newRestfulClient(MyClient.class, "http://foo");
		List<Patient> response = client.search("Patient:organization");

		assertThat(capt.getValue().getClass()).isEqualTo(HttpGet.class);
		HttpGet post = (HttpGet) capt.getValue();
		assertThat(post.getURI().toString()).isEqualTo("http://foo/Patient?_include=Patient%3Aorganization");
	}

	@Test
	public void testCreateWithInvalidType() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");
		String serialized = ourCtx.newXmlParser().encodeResourceToString(patient);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(serialized), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		try {
			ourCtx.newRestfulClient(ITestClientWithCreateWithInvalidParameterType.class, "http://foo");
			fail("");		} catch (ConfigurationException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1435) + "Method 'createPatient' is annotated with @ResourceParam but has a type that is not an implementation of org.hl7.fhir.instance.model.api.IBaseResource");
		}
	}

	@Test
	public void testCreateWithValidAndInvalidType() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");
		String serialized = ourCtx.newXmlParser().encodeResourceToString(patient);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(serialized), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		try {
			ourCtx.newRestfulClient(ITestClientWithCreateWithValidAndInvalidParameterType.class, "http://foo");
			fail("");		} catch (ConfigurationException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1438) + "Parameter #2/2 of method 'createPatient' on type 'ca.uhn.fhir.rest.client.ClientR4Test.ITestClientWithCreateWithValidAndInvalidParameterType' has no recognized FHIR interface parameter annotations. Don't know how to handle this parameter");
		}
	}

	@Test
	public void testDelete() throws Exception {

		OperationOutcome oo = new OperationOutcome();
		oo.addIssue().setDiagnostics("Hello");
		String resp = ourCtx.newXmlParser().encodeResourceToString(oo);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(resp), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome response = client.deletePatient(new IdType("1234"));

		assertThat(capt.getValue().getClass()).isEqualTo(HttpDelete.class);
		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/1234");
		assertThat(((OperationOutcome) response.getOperationOutcome()).getIssueFirstRep().getDiagnosticsElement().getValue()).isEqualTo("Hello");
	}

	@Test
	public void testDeleteNoResponse() throws Exception {

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 204, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.deleteDiagnosticReport(new IdType("1234"));

		assertThat(capt.getValue().getClass()).isEqualTo(HttpDelete.class);
		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/DiagnosticReport/1234");
	}

	@Test
	public void testGetConformance() throws Exception {

		CapabilityStatement cs = new CapabilityStatement();
		cs.getPublisherElement().setValue("Health Intersections");
		String msg = ourCtx.newXmlParser().encodeResourceToString(cs);


		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		CapabilityStatement response = (CapabilityStatement) client.getServerConformanceStatement();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/metadata");
		assertThat(response.getPublisherElement().getValue()).isEqualTo("Health Intersections");

	}

	@Test
	public void testHistoryResourceInstance() throws Exception {

		String msg = getHistoryBundleWithTwoResults();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML_NEW + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		Bundle response = client.getHistoryPatientInstance(new IdType("111"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/111/_history");

		assertThat(response.getEntry()).hasSize(2);

		verifyHistoryBundleWithTwoResults(response);
	}

	@Test
	public void testHistoryResourceType() throws Exception {

		String msg = getHistoryBundleWithTwoResults();
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML_NEW + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		Bundle response = client.getHistoryPatientType();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/_history");

		verifyHistoryBundleWithTwoResults(response);
	}

	@Test
	public void testHistoryServer() throws Exception {
		String msg = getHistoryBundleWithTwoResults();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML_NEW + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		Bundle response = client.getHistoryServer();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/_history");

		assertThat(response.getEntry()).hasSize(2);

		verifyHistoryBundleWithTwoResults(response);
	}

	@Test
	public void testHistoryWithParams() throws Exception {

		final String msg = getHistoryBundleWithTwoResults();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML_NEW + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8);
			}
		});

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");

		// ensures the local timezone
		String expectedDateString = new InstantType(new InstantType("2012-01-02T12:01:02").getValue()).getValueAsString();
		expectedDateString = expectedDateString.replace(":", "%3A").replace("+", "%2B");

		client.getHistoryPatientInstance(new IdType("111"), new InstantType("2012-01-02T12:01:02"), new IntegerType(12));
		assertThat(capt.getAllValues().get(0).getURI().toString()).contains("http://foo/Patient/111/_history?");
		assertThat(capt.getAllValues().get(0).getURI().toString()).contains("_since=" + expectedDateString.replaceAll("\\..*", ""));
		assertThat(capt.getAllValues().get(0).getURI().toString()).contains("_count=12");

		client.getHistoryPatientInstance(new IdType("111"), new InstantType("2012-01-02T12:01:02").getValue(), new IntegerType(12).getValue());
		assertThat(capt.getAllValues().get(1).getURI().toString()).contains("http://foo/Patient/111/_history?");
		assertThat(capt.getAllValues().get(1).getURI().toString()).contains("_since=" + expectedDateString);
		assertThat(capt.getAllValues().get(1).getURI().toString()).contains("_count=12");

		client.getHistoryPatientInstance(new IdType("111"), null, new IntegerType(12));
		assertThat(capt.getAllValues().get(2).getURI().toString()).isEqualTo("http://foo/Patient/111/_history?_count=12");

		client.getHistoryPatientInstance(new IdType("111"), new InstantType("2012-01-02T00:01:02"), null);
		assertThat(capt.getAllValues().get(3).getURI().toString()).isEqualTo("http://foo/Patient/111/_history?_since=2012-01-02T00%3A01%3A02");

		client.getHistoryPatientInstance(new IdType("111"), new InstantType(), new IntegerType(12));
		assertThat(capt.getAllValues().get(4).getURI().toString()).isEqualTo("http://foo/Patient/111/_history?_count=12");

		client.getHistoryPatientInstance(new IdType("111"), new InstantType("2012-01-02T00:01:02"), new IntegerType());
		assertThat(capt.getAllValues().get(5).getURI().toString()).isEqualTo("http://foo/Patient/111/_history?_since=2012-01-02T00%3A01%3A02");

	}

	@Test
	public void testNonAnnotatedMethodFailsGracefully() {

		// TODO: remove the read annotation and make sure we get a sensible
		// error message to tell the user why the method isn't working
		FhirContext ctx = ourCtx;
		ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

		ClientWithoutAnnotation client = ctx.newRestfulClient(ClientWithoutAnnotation.class, "http://wildfhir.aegis.net/fhir");

		try {
			client.read(new IdType("8"));
			fail("");		} catch (UnsupportedOperationException e) {
			assertThat(e.getMessage()).contains("annotation");
		}

	}

	@Test
	public void testRead() throws Exception {

		String msg = getPatient();

		ourLog.info(msg);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		Header[] headers = new Header[]{
			new BasicHeader(Constants.HEADER_LAST_MODIFIED, "Wed, 15 Nov 1995 04:58:08 GMT"),
			new BasicHeader(Constants.HEADER_CONTENT_LOCATION, "http://foo.com/Patient/123/_history/2333")
		};

		when(myHttpResponse.getAllHeaders()).thenReturn(headers);
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		// Patient response = client.findPatientByMrn(new
		// IdentifierDt("urn:foo", "123"));
		Patient response = client.getPatientById(new IdType("111"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/111");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

		assertThat(response.getId()).isEqualTo("http://foo.com/Patient/123/_history/2333");

		InstantType lm = response.getMeta().getLastUpdatedElement();
		lm.setTimeZoneZulu(true);
		assertThat(lm.getValueAsString()).isEqualTo("1995-11-15T04:58:08.000Z");

		ourLog.debug(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(response));

		List<Coding> tags = response.getMeta().getTag();
		assertThat(tags).isNotNull();
		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getCode()).isEqualTo("http://foo/tagdefinition.html");
		assertThat(tags.get(0).getSystem()).isEqualTo("http://hl7.org/fhir/tag");
		assertThat(tags.get(0).getDisplay()).isEqualTo("Some tag");

	}

	@Test
	public void testReadFailureInternalError() throws Exception {

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 500, "INTERNAL"));
		Header[] headers = new Header[1];
		headers[0] = new BasicHeader(Constants.HEADER_LAST_MODIFIED, "2011-01-02T22:01:02");
		when(myHttpResponse.getAllHeaders()).thenReturn(headers);
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader("Internal Failure"), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		try {
			client.getPatientById(new IdType("111"));
			fail("");		} catch (InternalErrorException e) {
			assertThat(e.getMessage()).contains("INTERNAL");
			assertThat(e.getResponseBody()).contains("Internal Failure");
		}

	}

	@Test
	public void testReadFailureNoCharset() throws Exception {

		//@formatter:off
		String msg = "<OperationOutcome xmlns=\"http://hl7.org/fhir\"></OperationOutcome>";
		//@formatter:on

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 404, "NOT FOUND"));
		Header[] headers = new Header[1];
		headers[0] = new BasicHeader(Constants.HEADER_LAST_MODIFIED, "2011-01-02T22:01:02");
		when(myHttpResponse.getAllHeaders()).thenReturn(headers);
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		try {
			client.getPatientById(new IdType("111"));
			fail("");		} catch (ResourceNotFoundException e) {
			// good
		}

	}

	@Test
	public void testReadNoCharset() throws Exception {

		String msg = getPatient();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		Header[] headers = new Header[1];
		headers[0] = new BasicHeader(Constants.HEADER_LAST_MODIFIED, "Wed, 15 Nov 1995 04:58:08 GMT");
		when(myHttpResponse.getAllHeaders()).thenReturn(headers);
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		// Patient response = client.findPatientByMrn(new
		// IdentifierDt("urn:foo", "123"));
		Patient response = client.getPatientById(new IdType("111"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/111");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

		InstantType lm = response.getMeta().getLastUpdatedElement();
		lm.setTimeZoneZulu(true);
		assertThat(lm.getValueAsString()).isEqualTo("1995-11-15T04:58:08.000Z");

	}

	@Test
	public void testResponseContainingOldStyleXmlContentType() throws Exception {

		String msg = getPatient();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", "application/fhir+xml; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		// Patient response = client.findPatientByMrn(new
		// IdentifierDt("urn:foo", "123"));
		Patient response = client.getPatientById(new IdType("111"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/111");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchByCompartment() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		List<Patient> response = client.getPatientByCompartmentAndDob(new IdType("123"), new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/123/compartmentName?birthdate=ge2011-01-02");
		assertThat(response.get(0).getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

		try {
			client.getPatientByCompartmentAndDob(new IdType(""), new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));
			fail("");		} catch (InvalidRequestException e) {
			assertThat(e.toString()).contains("null or empty for compartment");
		}

	}

	@Test
	public void testSearchByCompositeParam() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		StringParam str = new StringParam("FOO$BAR");
		DateParam date = new DateParam("2001-01-01");
		client.getObservationByNameValueDate(new CompositeParam<StringParam, DateParam>(str, date));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Observation?" + Observation.SP_CODE_VALUE_DATE + "=" + UrlUtil.escapeUrlParam("FOO\\$BAR$2001-01-01"));

	}

	@Test
	public void testSearchByDateRange() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		DateRangeParam param = new DateRangeParam();
		param.setLowerBound(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-01"));
		param.setUpperBound(new DateParam(ParamPrefixEnum.LESSTHAN_OR_EQUALS, "2021-01-01"));
		client.getPatientByDateRange(param);

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?dateRange=ge2011-01-01&dateRange=le2021-01-01");

	}

	@Test
	public void testSearchByDob() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		// httpResponse = new BasicHttpResponse(statusline, catalog, locale)
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		List<Patient> response = client.getPatientByDob(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");
		assertThat(response.get(0).getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchByQuantity() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		Patient response = client.findPatientQuantity(new QuantityParam(ParamPrefixEnum.GREATERTHAN, 123L, "foo", "bar"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?quantityParam=gt123%7Cfoo%7Cbar");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchByToken() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		Patient response = client.findPatientByMrn(new TokenParam("urn:foo", "123"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?identifier=urn%3Afoo%7C123");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchNamedQueryNoParams() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.getPatientNoParams();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?_query=someQueryNoParams");

	}

	@Test
	public void testSearchNamedQueryOneParam() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.getPatientOneParam(new StringParam("BB"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?_query=someQueryOneParam&param1=BB");

	}

	@Test
	public void testSearchOrList() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		TokenOrListParam identifiers = new TokenOrListParam();
		identifiers.add("foo", "bar");
		identifiers.add("baz", "boz");
		client.getPatientMultipleIdentifiers(identifiers);

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?ids=foo%7Cbar%2Cbaz%7Cboz");

	}

	@Test
	public void testSearchWithCustomType() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClientWithCustomType client = ourCtx.newRestfulClient(ITestClientWithCustomType.class, "http://foo");
		CustomPatient response = client.getPatientByDob(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchWithCustomTypeList() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClientWithCustomTypeList client = ourCtx.newRestfulClient(ITestClientWithCustomTypeList.class, "http://foo");
		List<CustomPatient> response = client.getPatientByDob(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");
		assertThat(response.get(0).getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchWithElements() throws Exception {

		final String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8);
			}
		});

		// httpResponse = new BasicHttpResponse(statusline, catalog, locale)
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

		ITestClientWithElements client = ourCtx.newRestfulClient(ITestClientWithElements.class, "http://foo");

		int idx = 0;

		client.getPatientWithIncludes((String) null);
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient");
		idx++;

		client.getPatientWithIncludes((Set<String>) null);
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient");
		idx++;

		client.getPatientWithIncludes("test");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient?_elements=test");
		idx++;

		client.getPatientWithIncludes("test,foo");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient?_elements=test%2Cfoo");
		idx++;

		client.getPatientWithIncludes(new HashSet<String>(Arrays.asList("test", "foo", "")));
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient?_elements=test%2Cfoo");
		idx++;

	}

	@Test
	public void testSearchWithEscapedValues() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		StringAndListParam andListParam = new StringAndListParam();
		StringOrListParam orListParam1 = new StringOrListParam().addOr(new StringParam("NE,NE", false)).addOr(new StringParam("NE,NE", false));
		StringOrListParam orListParam2 = new StringOrListParam().addOr(new StringParam("E$E", true));
		StringOrListParam orListParam3 = new StringOrListParam().addOr(new StringParam("NE\\NE", false));
		StringOrListParam orListParam4 = new StringOrListParam().addOr(new StringParam("E|E", true));
		client.findPatient(andListParam.addAnd(orListParam1).addAnd(orListParam2).addAnd(orListParam3).addAnd(orListParam4));

		assertThat(capt.getValue().getURI().toString()).contains("%3A");
		assertThat(UrlUtil.unescape(capt.getValue().getURI().toString())).isEqualTo("http://foo/Patient?param=NE\\,NE,NE\\,NE&param=NE\\\\NE&param:exact=E\\$E&param:exact=E\\|E");

	}

	@Test
	public void testSearchWithFormatAndPrettyPrint() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		// TODO: document this

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.getPatientByDob(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));
		assertThat(capt.getAllValues().get(0).getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");

		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));
		client.setEncoding(EncodingEnum.JSON); // this needs to be actually
		// implemented
		client.getPatientByDob(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));
		assertThat(capt.getAllValues().get(1).getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02&_format=json");

		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));
		client.setPrettyPrint(true);
		client.getPatientByDob(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));
		assertThat(capt.getAllValues().get(2).getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02&_format=json&_pretty=true");

	}

	@Test
	public void testSearchWithGenericReturnType() throws Exception {

		Bundle bundle = new Bundle();

		Patient patient = new Patient();
		patient.addIdentifier().setValue("PRP1660");
		bundle.addEntry().setResource(patient);

		Organization org = new Organization();
		org.setName("FOO");
		patient.getManagingOrganization().setResource(org);

		String msg = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		// httpResponse = new BasicHttpResponse(statusline, catalog, locale)
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		List<IBaseResource> response = client.getPatientByDobWithGenericResourceReturnType(new DateParam(ParamPrefixEnum.GREATERTHAN_OR_EQUALS, "2011-01-02"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?birthdate=ge2011-01-02");
		ExtendedPatient patientResp = (ExtendedPatient) response.get(0);
		assertThat(patientResp.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchWithGlobalSummary() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.setSummary(SummaryEnum.DATA);
		client.findPatientByMrn(new TokenParam("sysm", "val"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?identifier=sysm%7Cval&_summary=data");

	}

	@Test
	public void testSearchWithIncludes() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.getPatientWithIncludes(new StringParam("aaa"), Arrays.asList(new Include("inc1"), new Include("inc2", true), new Include("inc3", true)));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?withIncludes=aaa&_include=inc1&_include%3Aiterate=inc2&_include%3Aiterate=inc3");

	}

	@Test
	public void testSearchWithAt() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		client.getPatientWithAt(new InstantType("2010-10-01T01:02:03.0Z"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?_at=2010-10-01T01%3A02%3A03.0Z");

	}

	@Test
	public void testUnannotatedMethod() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClientWithUnannotatedMethod client = ourCtx.newRestfulClient(ITestClientWithUnannotatedMethod.class, "http://foo");
		try {
			client.getPatientWithAt(new InstantType("2010-10-01T01:02:03.0Z"));
			fail("");		} catch (UnsupportedOperationException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1403) + "The method 'getPatientWithAt' in type ITestClientWithUnannotatedMethod has no handler. Did you forget to annotate it with a RESTful method annotation?");
		}

	}

	@Test
	public void testSearchWithOptionalParam() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		Bundle response = client.findPatientByName(new StringParam("AAA"), null);

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?family=AAA");
		Patient resource = (Patient) response.getEntry().get(0).getResource();
		assertThat(resource.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

		/*
		 * Now with a first name
		 */

		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));
		client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		response = client.findPatientByName(new StringParam("AAA"), new StringParam("BBB"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?family=AAA&given=BBB");
		resource = (Patient) response.getEntry().get(0).getResource();
		assertThat(resource.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testSearchWithStringIncludes() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClientWithStringIncludes client = ourCtx.newRestfulClient(ITestClientWithStringIncludes.class, "http://foo");
		client.getPatientWithIncludes(new StringParam("aaa"), "inc1");

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?withIncludes=aaa&_include=inc1");

	}

	@Test
	public void testSearchWithSummary() throws Exception {

		final String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);

		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8);
			}
		});

		// httpResponse = new BasicHttpResponse(statusline, catalog, locale)
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);

		ITestClientWithSummary client = ourCtx.newRestfulClient(ITestClientWithSummary.class, "http://foo");

		int idx = 0;

		client.getPatientWithIncludes((SummaryEnum) null);
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient");
		idx++;

		client.getPatientWithIncludes(SummaryEnum.COUNT);
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient?_summary=count");
		idx++;

		client.getPatientWithIncludes(SummaryEnum.DATA);
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient?_summary=data");
		idx++;

		client.getPatientWithIncludes(Arrays.asList(SummaryEnum.DATA));
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient?_summary=data");
		idx++;

		client.getPatientWithIncludes(Arrays.asList(SummaryEnum.COUNT, SummaryEnum.DATA));
		assertThat(capt.getAllValues().get(idx).getURI().toString(), either(equalTo("http://foo/Patient?_summary=data&_summary=count")).or(equalTo("http://foo/Patient?_summary=count&_summary=data")));
		idx++;

		client.getPatientWithIncludes(new ArrayList<SummaryEnum>());
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo/Patient");
		idx++;
	}

	@Test
	public void testUpdate() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome response = client.updatePatient(new IdType("100"), patient);

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPut.class);
		HttpPut post = (HttpPut) capt.getValue();
		assertThat(post.getURI().toASCIIString(), StringEndsWith.endsWith("/Patient/100"));
		assertThat(IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8), StringContains.containsString("\"Patient"));
		assertThat(response.getId().getValue()).isEqualTo("http://example.com/fhir/Patient/100/_history/200");
		assertThat(response.getId().getVersionIdPart()).isEqualTo("200");
		assertThat(capt.getAllValues().get(0).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.JSON.getResourceContentTypeNonLegacy() + Constants.HEADER_SUFFIX_CT_UTF_8);
	}

	/**
	 * Return a FHIR content type, but no content and make sure we handle this without crashing
	 */
	@Test
	public void testUpdateWithEmptyResponse() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray(Constants.HEADER_LOCATION, "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome resp = client.updatePatient(new IdType("Patient/100/_history/200"), patient);
		assertThat(resp.getResource()).isNull();
		assertThat(resp.getOperationOutcome()).isNull();

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPut.class);
		HttpPut post = (HttpPut) capt.getValue();
		assertThat(post.getURI().toASCIIString()).isEqualTo("http://foo/Patient/100");

	}

	@Test
	public void testUpdateWithResourceConflict() throws Exception {
		try {
			Patient patient = new Patient();
			patient.addIdentifier().setSystem("urn:foo").setValue("123");

			ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
			when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
			when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
			when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
			when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_409_CONFLICT, "Conflict"));

			ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
			client.updatePatient(new IdType("Patient/100/_history/200"), patient);
			fail("");		} catch (ResourceVersionConflictException e) {
			assertThat(e.getMessage()).isEqualTo("HTTP 409 Conflict");
		}
	}

	@Test
	public void testUpdateWithVersion() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 201, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome response = client.updatePatient(new IdType("Patient/100/_history/200"), patient);

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPut.class);
		HttpPut post = (HttpPut) capt.getValue();
		assertThat(post.getURI().toASCIIString(), StringEndsWith.endsWith("/Patient/100"));
		assertThat(IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8), StringContains.containsString("\"Patient"));
		assertThat(response.getId().getValue()).isEqualTo("http://example.com/fhir/Patient/100/_history/200");
		assertThat(response.getId().getVersionIdPart()).isEqualTo("200");
	}

	@Test
	public void testValidateNoContentResponse() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(""), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome response = client.validatePatient(patient);

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPost.class);
		HttpPost post = (HttpPost) capt.getValue();
		assertThat(post.getURI().toASCIIString(), StringEndsWith.endsWith("/Patient/$validate"));
		assertThat(IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8), StringContains.containsString("\"Patient"));
		assertThat(response.getOperationOutcome()).isNull();
		assertThat(response.getResource()).isNull();
	}

	@Test
	public void testValidateServerBaseWithInvalidResponse() throws Exception {

		String response = "AAAAAAA";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(response), StandardCharsets.UTF_8));

		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.ONCE);
		IGenericClient client = ourCtx.newRestfulGenericClient("http://testValidateServerBaseWithInvalidResponse");
		try {
			client.read().resource("Patient").withId("1").execute();
			fail("");		} catch (FhirClientConnectionException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1357) + "Failed to retrieve the server metadata statement during client initialization. URL used was http://testValidateServerBaseWithInvalidResponse/metadata");
		}

	}

	@Test
	public void testValidateOutcomeResponse() throws Exception {

		OperationOutcome oo = new OperationOutcome();
		oo.addIssue().setDiagnostics("ALL GOOD");
		String resp = ourCtx.newJsonParser().encodeResourceToString(oo);

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("urn:foo").setValue("123");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON_NEW + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(resp), StandardCharsets.UTF_8));
		when(myHttpResponse.getAllHeaders()).thenReturn(toHeaderArray("Location", "http://example.com/fhir/Patient/100/_history/200"));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		MethodOutcome response = client.validatePatient(patient);

		assertThat(capt.getValue().getClass()).isEqualTo(HttpPost.class);
		HttpPost post = (HttpPost) capt.getValue();
		assertThat(post.getURI().toASCIIString(), StringEndsWith.endsWith("/Patient/$validate"));
		assertThat(IOUtils.toString(post.getEntity().getContent(), Charsets.UTF_8), StringContains.containsString("\"Patient"));
		assertThat(response.getOperationOutcome()).isNotNull();
		assertThat(((OperationOutcome) response.getOperationOutcome()).getIssueFirstRep().getDiagnostics()).isEqualTo("ALL GOOD");
		assertThat(response.getResource()).isNull();
	}

	@Test
	public void testVRead() throws Exception {

		//@formatter:off
		String msg = "<Patient xmlns=\"http://hl7.org/fhir\">"
			+ "<text><status value=\"generated\" /><div xmlns=\"http://www.w3.org/1999/xhtml\">John Cardinal:            444333333        </div></text>"
			+ "<identifier><label value=\"SSN\" /><system value=\"http://orionhealth.com/mrn\" /><value value=\"PRP1660\" /></identifier>"
			+ "<name><use value=\"official\" /><family value=\"Cardinal\" /><given value=\"John\" /></name>"
			+ "<name><family value=\"Kramer\" /><given value=\"Doe\" /></name>"
			+ "<telecom><system value=\"phone\" /><value value=\"555-555-2004\" /><use value=\"work\" /></telecom>"
			+ "<gender><coding><system value=\"http://hl7.org/fhir/v3/AdministrativeGender\" /><code value=\"M\" /></coding></gender>"
			+ "<address><use value=\"home\" /><line value=\"2222 Home Street\" /></address><active value=\"true\" />"
			+ "</Patient>";
		//@formatter:on

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), StandardCharsets.UTF_8));

		ITestClient client = ourCtx.newRestfulClient(ITestClient.class, "http://foo");
		// Patient response = client.findPatientByMrn(new
		// IdentifierDt("urn:foo", "123"));
		Patient response = client.getPatientById(new IdType("Patient/111/_history/999"));

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient/111/_history/999");
		assertThat(response.getIdentifier().get(0).getValueElement().getValue()).isEqualTo("PRP1660");

	}

	@Test
	public void testClientWithAndOrList() throws IOException {

		Bundle response = new Bundle().setType(Bundle.BundleType.SEARCHSET);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity()).thenReturn(new ResourceEntity(ourCtx, response));

		ITestClientWithAndOr client = ourCtx.newRestfulClient(ITestClientWithAndOr.class, "http://foo");
		StringAndListParam andList = new StringAndListParam();
		StringOrListParam orListA = new StringOrListParam();
		orListA.add(new StringParam("A1"));
		orListA.add(new StringParam("A2"));
		andList.addAnd(orListA);
		StringOrListParam orListB = new StringOrListParam();
		orListB.add(new StringParam("B1"));
		orListB.add(new StringParam("B2"));
		andList.addAnd(orListB);
		client.search(andList);

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?foo=A1%2CA2&foo=B1%2CB2");
	}

	@Test
	public void testClientWithAndOrList2() throws IOException {

		Bundle response = new Bundle().setType(Bundle.BundleType.SEARCHSET);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity()).thenReturn(new ResourceEntity(ourCtx, response));

		try {
			ourCtx.newRestfulClient(ITestClientWithAndOr2.class, "http://foo");
		} catch (ConfigurationException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1433) + "Argument #0 of Method 'search' in type 'ca.uhn.fhir.rest.client.ClientR4Test.ITestClientWithAndOr2' is of an invalid generic type (can not be a collection of a collection of a collection)");
		}
	}

	@Test
	public void testClientWithAndOrList3() throws IOException {

		Bundle response = new Bundle().setType(Bundle.BundleType.SEARCHSET);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity()).thenReturn(new ResourceEntity(ourCtx, response));

		ITestClientWithAndOr3 client = ourCtx.newRestfulClient(ITestClientWithAndOr3.class, "http://foo");
		Set<Include> orListA = new HashSet<>();
		orListA.add(new Include("a"));
		orListA.add(new Include("b"));
		client.search(orListA);

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://foo/Patient?_include=a&_include=b");
	}

	private Header[] toHeaderArray(String theName, String theValue) {
		return new Header[]{new BasicHeader(theName, theValue)};
	}

	private void verifyHistoryBundleWithTwoResults(Bundle response) {
		assertThat(response.getEntry()).hasSize(2);
		// Older resource
		{
			BundleEntryComponent olderEntry = response.getEntry().get(0);
			assertThat(olderEntry.getResource().getId()).isEqualTo("http://acme.com/Patient/111");
		}
		// Newer resource
		{
			BundleEntryComponent newerEntry = response.getEntry().get(1);
			assertThat(newerEntry.getResource().getId()).isEqualTo("http://acme.com/Patient/222");
		}
	}

	interface MyClient extends IRestfulClient {

		@Search()
		List<Patient> search(@IncludeParam String theInclude);


	}

	public interface ITestClientWithCreateWithInvalidParameterType extends IRestfulClient {

		@Create()
		MethodOutcome createPatient(@ResourceParam int thePatient);
	}

	public interface ITestClientWithCreateWithValidAndInvalidParameterType extends IRestfulClient {

		@Create()
		MethodOutcome createPatient(@ResourceParam Patient thePatient, int theInt);
	}

	interface ITestClientWithAndOr extends IBasicClient {

		@Search()
		List<Patient> search(@OptionalParam(name = "foo") StringAndListParam theParam);

	}

	interface ITestClientWithAndOr2 extends IBasicClient {

		@Search()
		List<Patient> search(@OptionalParam(name = "foo") List<List<String>> theParam);

	}

	public interface ITestClientWithAndOr3 extends IBasicClient {

		@Search()
		List<Patient> search(@IncludeParam Set<Include> theParam);

	}

	private interface ClientWithoutAnnotation extends IBasicClient {
		Patient read(@IdParam IdType theId);
	}

	public interface ITestClientWithCustomType extends IBasicClient {
		@Search()
		CustomPatient getPatientByDob(@RequiredParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate);
	}

	public interface ITestClientWithCustomTypeList extends IBasicClient {
		@Search()
		List<CustomPatient> getPatientByDob(@RequiredParam(name = Patient.SP_BIRTHDATE) DateParam theBirthDate);
	}

	public interface ITestClientWithElements extends IBasicClient {
		@Search()
		List<Patient> getPatientWithIncludes(@Elements Set<String> theElements);

		@Search()
		List<Patient> getPatientWithIncludes(@Elements String theElements);

	}

	public interface ITestClientWithStringIncludes extends IBasicClient {
		@Search()
		Patient getPatientWithIncludes(@RequiredParam(name = "withIncludes") StringParam theString, @IncludeParam String theInclude);
	}

	public interface ITestClientWithSummary extends IBasicClient {
		@Search()
		List<Patient> getPatientWithIncludes(List<SummaryEnum> theSummary);

		@Search()
		List<Patient> getPatientWithIncludes(SummaryEnum theSummary);

	}

	interface ITestClientWithUnannotatedMethod extends IRestfulClient {
		void getPatientWithAt(@At InstantType theInstantType);
	}

	@ResourceDef(name = "Patient")
	public static class CustomPatient extends Patient {

		private static final long serialVersionUID = 1L;

		// nothing
	}

	@AfterAll
	public static void afterClassClearContext() {
		TestUtil.randomizeLocaleAndTimezone();
	}

	private static String getPatientFeedWithOneResult() {
		return getPatientFeedWithOneResult(ourCtx);
	}

	static String getPatientFeedWithOneResult(FhirContext theCtx) {

		Bundle retVal = new Bundle();

		Patient p = new Patient();
		p.addName().setFamily("Cardinal").addGiven("John");
		p.addIdentifier().setValue("PRP1660");
		retVal.addEntry().setResource(p);

		return theCtx.newXmlParser().encodeResourceToString(retVal);
	}

}
