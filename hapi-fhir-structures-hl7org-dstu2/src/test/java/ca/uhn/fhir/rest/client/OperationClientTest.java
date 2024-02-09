package ca.uhn.fhir.rest.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.client.api.IBasicClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.dstu2.model.IdType;
import org.hl7.fhir.dstu2.model.Parameters;
import org.hl7.fhir.dstu2.model.Patient;
import org.hl7.fhir.dstu2.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OperationClientTest {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OperationClientTest.class);
	private FhirContext ourCtx;
	private HttpClient ourHttpClient;

	private HttpResponse ourHttpResponse;

	@BeforeEach
	public void before() {
		ourCtx = FhirContext.forDstu2Hl7Org();

		ourHttpClient = mock(HttpClient.class, new ReturnsDeepStubs());
		ourCtx.getRestfulClientFactory().setHttpClient(ourHttpClient);
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

		ourHttpResponse = mock(HttpResponse.class, new ReturnsDeepStubs());
	}

	@Test
	public void testOpInstance() throws Exception {
		Parameters outParams = new Parameters();
		outParams.addParameter().setName("FOO");
		final String retVal = ourCtx.newXmlParser().encodeResourceToString(outParams);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(ourHttpClient.execute(capt.capture())).thenReturn(ourHttpResponse);
		when(ourHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(ourHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(ourHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(retVal), Charset.forName("UTF-8"));
			}
		});

		IOpClient client = ourCtx.newRestfulClient(IOpClient.class, "http://foo");

		int idx = 0;

		Parameters response = client.opInstance(new IdType("222"), new StringType("PARAM1str"), new Patient().setActive(true));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		HttpPost value = (HttpPost) capt.getAllValues().get(idx);
		String requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		Parameters request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/Patient/222/$OP_INSTANCE");
		assertThat(request.getParameter()).hasSize(2);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("PARAM1");
		assertThat(((StringType) request.getParameter().get(0).getValue()).getValue()).isEqualTo("PARAM1str");
		assertThat(request.getParameter().get(1).getName()).isEqualTo("PARAM2");
		assertThat(((Patient) request.getParameter().get(1).getResource()).getActive()).isEqualTo(Boolean.TRUE);
		idx++;
	}

	@Test
	public void testOpServer() throws Exception {
		Parameters outParams = new Parameters();
		outParams.addParameter().setName("FOO");
		final String retVal = ourCtx.newXmlParser().encodeResourceToString(outParams);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(ourHttpClient.execute(capt.capture())).thenReturn(ourHttpResponse);
		when(ourHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(ourHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(ourHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(retVal), Charset.forName("UTF-8"));
			}
		});

		IOpClient client = ourCtx.newRestfulClient(IOpClient.class, "http://foo");

		int idx = 0;

		Parameters response = client.opServer(new StringType("PARAM1str"), new Patient().setActive(true));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		HttpPost value = (HttpPost) capt.getAllValues().get(idx);
		String requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		Parameters request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$OP_SERVER");
		assertThat(request.getParameter()).hasSize(2);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("PARAM1");
		assertThat(((StringType) request.getParameter().get(0).getValue()).getValue()).isEqualTo("PARAM1str");
		assertThat(request.getParameter().get(1).getName()).isEqualTo("PARAM2");
		assertThat(((Patient) request.getParameter().get(1).getResource()).getActive()).isEqualTo(Boolean.TRUE);
		idx++;

		response = client.opServer(null, new Patient().setActive(true));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		value = (HttpPost) capt.getAllValues().get(idx);
		requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(request.getParameter()).hasSize(1);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("PARAM2");
		assertThat(((Patient) request.getParameter().get(0).getResource()).getActive()).isEqualTo(Boolean.TRUE);
		idx++;

		response = client.opServer(null, null);
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		value = (HttpPost) capt.getAllValues().get(idx);
		requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(request.getParameter()).isEmpty();
		idx++;

	}

	@Test
	public void testOpWithListParam() throws Exception {
		Parameters outParams = new Parameters();
		outParams.addParameter().setName("FOO");
		final String retVal = ourCtx.newXmlParser().encodeResourceToString(outParams);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(ourHttpClient.execute(capt.capture())).thenReturn(ourHttpResponse);
		when(ourHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(ourHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(ourHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(retVal), Charset.forName("UTF-8"));
			}
		});

		IOpClient client = ourCtx.newRestfulClient(IOpClient.class, "http://foo");

		int idx = 0;

		Parameters response = client.opServerListParam(new Patient().setActive(true), Arrays.asList(new StringType("PARAM3str1"), new StringType("PARAM3str2")));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		HttpPost value = (HttpPost) capt.getAllValues().get(idx);
		String requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		Parameters request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$OP_SERVER_LIST_PARAM");
		assertThat(request.getParameter()).hasSize(3);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("PARAM2");
		assertThat(((Patient) request.getParameter().get(0).getResource()).getActive()).isEqualTo(Boolean.TRUE);
		assertThat(request.getParameter().get(1).getName()).isEqualTo("PARAM3");
		assertThat(((StringType) request.getParameter().get(1).getValue()).getValue()).isEqualTo("PARAM3str1");
		assertThat(request.getParameter().get(2).getName()).isEqualTo("PARAM3");
		assertThat(((StringType) request.getParameter().get(2).getValue()).getValue()).isEqualTo("PARAM3str2");
		idx++;

		response = client.opServerListParam(null, Arrays.asList(new StringType("PARAM3str1"), new StringType("PARAM3str2")));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		value = (HttpPost) capt.getAllValues().get(idx);
		requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$OP_SERVER_LIST_PARAM");
		assertThat(request.getParameter()).hasSize(2);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("PARAM3");
		assertThat(((StringType) request.getParameter().get(0).getValue()).getValue()).isEqualTo("PARAM3str1");
		assertThat(request.getParameter().get(1).getName()).isEqualTo("PARAM3");
		assertThat(((StringType) request.getParameter().get(1).getValue()).getValue()).isEqualTo("PARAM3str2");
		idx++;

		response = client.opServerListParam(null, new ArrayList<StringType>());
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		value = (HttpPost) capt.getAllValues().get(idx);
		requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$OP_SERVER_LIST_PARAM");
		assertThat(request.getParameter()).isEmpty();
		idx++;

		response = client.opServerListParam(null, null);
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		value = (HttpPost) capt.getAllValues().get(idx);
		requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/$OP_SERVER_LIST_PARAM");
		assertThat(request.getParameter()).isEmpty();
		idx++;


	}

	@Test
	public void testOpType() throws Exception {
		Parameters outParams = new Parameters();
		outParams.addParameter().setName("FOO");
		final String retVal = ourCtx.newXmlParser().encodeResourceToString(outParams);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(ourHttpClient.execute(capt.capture())).thenReturn(ourHttpResponse);
		when(ourHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(ourHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(ourHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(retVal), Charset.forName("UTF-8"));
			}
		});

		IOpClient client = ourCtx.newRestfulClient(IOpClient.class, "http://foo");

		int idx = 0;

		Parameters response = client.opType(new StringType("PARAM1str"), new Patient().setActive(true));
		assertThat(response.getParameter().get(0).getName()).isEqualTo("FOO");
		HttpPost value = (HttpPost) capt.getAllValues().get(idx);
		String requestBody = IOUtils.toString(((HttpPost) value).getEntity().getContent());
		IOUtils.closeQuietly(((HttpPost) value).getEntity().getContent());
		ourLog.info(requestBody);
		Parameters request = ourCtx.newJsonParser().parseResource(Parameters.class, requestBody);
		assertThat(value.getURI().toASCIIString()).isEqualTo("http://foo/Patient/$OP_TYPE");
		assertThat(request.getParameter()).hasSize(2);
		assertThat(request.getParameter().get(0).getName()).isEqualTo("PARAM1");
		assertThat(((StringType) request.getParameter().get(0).getValue()).getValue()).isEqualTo("PARAM1str");
		assertThat(request.getParameter().get(1).getName()).isEqualTo("PARAM2");
		assertThat(((Patient) request.getParameter().get(1).getResource()).getActive()).isEqualTo(Boolean.TRUE);
		idx++;
	}

	public interface IOpClient extends IBasicClient {
		//@formatter:off
		@Operation(name="$OP_INSTANCE", type=Patient.class)
		public Parameters opInstance(
				@IdParam IdType theId,
				@OperationParam(name="PARAM1") StringType theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				);
		//@formatter:on

		//@formatter:off
		@Operation(name="$OP_SERVER")
		public Parameters opServer(
				@OperationParam(name="PARAM1") StringType theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				);
		//@formatter:on

		//@formatter:off
		@Operation(name="$OP_SERVER_LIST_PARAM")
		public Parameters opServerListParam(
				@OperationParam(name="PARAM2") Patient theParam2,
				@OperationParam(name="PARAM3") List<StringType> theParam3
				);
		//@formatter:on

		//@formatter:off
		@Operation(name="$OP_TYPE", type=Patient.class)
		public Parameters opType(
				@OperationParam(name="PARAM1") StringType theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				);
		//@formatter:on

	}
}
