package ca.uhn.fhir.rest.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IBasicClient;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.param.StringAndListParam;
import ca.uhn.fhir.rest.param.StringOrListParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.StringType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OperationClientDstu3Test {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OperationClientDstu3Test.class);
	private FhirContext ourCtx;
	private HttpClient ourHttpClient;

	private HttpResponse ourHttpResponse;
	private IOpClient ourAnnClient;
	private ArgumentCaptor<HttpUriRequest> capt;
	private IGenericClient ourGenClient;


	@AfterAll
	public static void afterClassClearContext() {
		TestUtil.randomizeLocaleAndTimezone();
	}


	@BeforeEach
	public void before() throws Exception {
		ourCtx = FhirContext.forDstu3();

		ourHttpClient = mock(HttpClient.class, new ReturnsDeepStubs());
		ourCtx.getRestfulClientFactory().setHttpClient(ourHttpClient);
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

		ourHttpResponse = mock(HttpResponse.class, new ReturnsDeepStubs());
		
		Parameters outParams = new Parameters();
		outParams.addParameter().setName("FOO");
		final String retVal = ourCtx.newXmlParser().encodeResourceToString(outParams);

		capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(ourHttpClient.execute(capt.capture())).thenReturn(ourHttpResponse);
		when(ourHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(ourHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(ourHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(retVal), StandardCharsets.UTF_8);
			}
		});

		ourAnnClient = ourCtx.newRestfulClient(IOpClient.class, "http://foo");
		ourGenClient = ourCtx.newRestfulGenericClient("http://foo");
	}

	@Test
	public void testNonRepeatingGenericUsingParameters() throws Exception {
		ourGenClient
			.operation()
			.onServer()
			.named("nonrepeating")
			.withSearchParameter(Parameters.class, "valstr", new StringParam("str"))
			.andSearchParameter("valtok", new TokenParam("sys2", "val2"))
			.execute();
		Parameters response = ourAnnClient.nonrepeating(new StringParam("str"), new TokenParam("sys", "val"));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		
		HttpPost value = (HttpPost) capt.getAllValues().get(0);
		String requestBody = IOUtils.toString(value.getEntity().getContent());
		IOUtils.closeQuietly(value.getEntity().getContent());
		ourLog.info(requestBody);
		Parameters request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$nonrepeating");
		assertThat(request.getParameter()).hasSize(2);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("valstr");
		assertThat(((StringType) request.getParameter().get(0).getValue()).getValue()).isEqualTo("str");
		assertThat(request.getParameter().get(1).getName()).isEqualTo("valtok");
		assertThat(((StringType) request.getParameter().get(1).getValue()).getValue()).isEqualTo("sys2|val2");
	}

	@Test
	public void testNonRepeatingGenericUsingUrl() {
		ourGenClient
			.operation()
			.onServer()
			.named("nonrepeating")
			.withSearchParameter(Parameters.class, "valstr", new StringParam("str"))
			.andSearchParameter("valtok", new TokenParam("sys2", "val2"))
			.useHttpGet()
			.execute();
		Parameters response = ourAnnClient.nonrepeating(new StringParam("str"), new TokenParam("sys", "val"));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		
		HttpGet value = (HttpGet) capt.getAllValues().get(0);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$nonrepeating?valstr=str&valtok=sys2%7Cval2");
	}

	@Test
	public void testNonRepeatingUsingParameters() throws Exception {
		Parameters response = ourAnnClient.nonrepeating(new StringParam("str"), new TokenParam("sys", "val"));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		
		HttpPost value = (HttpPost) capt.getAllValues().get(0);
		String requestBody = IOUtils.toString(value.getEntity().getContent());
		IOUtils.closeQuietly(value.getEntity().getContent());
		ourLog.info(requestBody);
		Parameters request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$nonrepeating");
		assertThat(request.getParameter()).hasSize(2);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("valstr");
		assertThat(((StringType) request.getParameter().get(0).getValue()).getValue()).isEqualTo("str");
		assertThat(request.getParameter().get(1).getName()).isEqualTo("valtok");
		assertThat(((StringType) request.getParameter().get(1).getValue()).getValue()).isEqualTo("sys|val");
	}

	public interface IOpClient extends IBasicClient {

		@Operation(name = "$andlist", idempotent = true)
		Parameters andlist(
			//@formatter:off
			@OperationParam(name = "valstr", max = 10) StringAndListParam theValStr,
			@OperationParam(name = "valtok", max = 10) TokenAndListParam theValTok
			//@formatter:on
		);

		@Operation(name = "$andlist-withnomax", idempotent = true)
		Parameters andlistWithNoMax(
			//@formatter:off
			@OperationParam(name = "valstr") StringAndListParam theValStr,
			@OperationParam(name = "valtok") TokenAndListParam theValTok
			//@formatter:on
		);

		@Operation(name = "$nonrepeating", idempotent = true)
		Parameters nonrepeating(
			//@formatter:off
			@OperationParam(name = "valstr") StringParam theValStr,
			@OperationParam(name = "valtok") TokenParam theValTok
			//@formatter:on
		);

		@Operation(name = "$orlist", idempotent = true)
		Parameters orlist(
			//@formatter:off
			@OperationParam(name = "valstr", max = 10) List<StringOrListParam> theValStr,
			@OperationParam(name = "valtok", max = 10) List<TokenOrListParam> theValTok
			//@formatter:on
		);

		@Operation(name = "$orlist-withnomax", idempotent = true)
		Parameters orlistWithNoMax(
			//@formatter:off
			@OperationParam(name = "valstr") List<StringOrListParam> theValStr,
			@OperationParam(name = "valtok") List<TokenOrListParam> theValTok
			//@formatter:on
		);

	}
}
