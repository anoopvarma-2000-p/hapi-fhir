package ca.uhn.fhir.rest.client;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.api.IInterceptorService;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.dstu2.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu2.composite.MetaDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Link;
import ca.uhn.fhir.model.dstu2.resource.Conformance;
import ca.uhn.fhir.model.dstu2.resource.Conformance.Rest;
import ca.uhn.fhir.model.dstu2.resource.Conformance.RestSecurity;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.OperationOutcome;
import ca.uhn.fhir.model.dstu2.resource.Parameters;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.primitive.CodeDt;
import ca.uhn.fhir.model.primitive.DateDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.model.primitive.UriDt;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.XmlParserDstu2Test;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import ca.uhn.fhir.rest.api.RequestFormatParamStyleEnum;
import ca.uhn.fhir.rest.api.SearchStyleEnum;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IHttpClient;
import ca.uhn.fhir.rest.client.api.IRestfulClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.exceptions.InvalidResponseException;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.system.HapiSystemProperties;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static ca.uhn.fhir.test.utilities.getMethodNameUtil.getTestName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.equalTo;
import static org.assertj.core.api.Assertions.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenericClientDstu2Test {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(GenericClientDstu2Test.class);
	private static FhirContext ourCtx;
	private HttpClient myHttpClient;

	private HttpResponse myHttpResponse;

	private int myResponseCount = 0;

	@BeforeEach
	public void before() {
		myHttpClient = mock(HttpClient.class, new ReturnsDeepStubs());
		ourCtx.setRestfulClientFactory(new ApacheRestfulClientFactory(ourCtx));
		ourCtx.getRestfulClientFactory().setConnectionRequestTimeout(10000);
		ourCtx.getRestfulClientFactory().setConnectTimeout(10000);
		ourCtx.getRestfulClientFactory().setPoolMaxPerRoute(100);
		ourCtx.getRestfulClientFactory().setPoolMaxTotal(100);

		ourCtx.getRestfulClientFactory().setHttpClient(myHttpClient);
		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
		myHttpResponse = mock(HttpResponse.class, new ReturnsDeepStubs());
		myResponseCount = 0;

		HapiSystemProperties.enableHapiClientKeepResponses();
	}

	private String extractBody(ArgumentCaptor<HttpUriRequest> capt, int count) throws IOException {
		String body = IOUtils.toString(((HttpEntityEnclosingRequestBase) capt.getAllValues().get(count)).getEntity().getContent(), "UTF-8");
		return body;
	}

	private String getPatientFeedWithOneResult() {
		//@formatter:off
		String msg = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
			"<id>d039f91a-cc3c-4013-988e-af4d8d0614bd</id>\n" +
			"<entry>\n" +
			"<resource>"
			+ "<Patient>"
			+ "<text><status value=\"generated\" /><div xmlns=\"http://www.w3.org/1999/xhtml\">John Cardinal:            444333333        </div></text>"
			+ "<identifier><label value=\"SSN\" /><system value=\"http://orionhealth.com/mrn\" /><value value=\"PRP1660\" /></identifier>"
			+ "<name><use value=\"official\" /><family value=\"Cardinal\" /><given value=\"John\" /></name>"
			+ "<name><family value=\"Kramer\" /><given value=\"Doe\" /></name>"
			+ "<telecom><system value=\"phone\" /><value value=\"555-555-2004\" /><use value=\"work\" /></telecom>"
			+ "<address><use value=\"home\" /><line value=\"2222 Home Street\" /></address><active value=\"true\" />"
			+ "</Patient>"
			+ "</resource>\n"
			+ "   </entry>\n"
			+ "</Bundle>";
		//@formatter:on
		return msg;
	}

	@Test
	public void testAcceptHeaderFetchConformance() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Conformance conf = new Conformance();
		conf.setCopyright("COPY");

		final String respString = p.encodeResourceToString(conf);
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

		int idx = 0;

		client.fetchConformance().ofType(Conformance.class).execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/metadata");
		assertThat(capt.getAllValues().get(idx).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders("Accept")[0].getValue()).contains(Constants.HEADER_ACCEPT_VALUE_XML_OR_JSON_LEGACY);
		idx++;

		client.fetchConformance().ofType(Conformance.class).encodedJson().execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/metadata?_format=json");
		assertThat(capt.getAllValues().get(idx).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_JSON);
		idx++;

		client.fetchConformance().ofType(Conformance.class).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/metadata?_format=xml");
		assertThat(capt.getAllValues().get(idx).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_XML);
		idx++;
	}

	@Test
	public void testAcceptHeaderPreflightConformance() throws Exception {
		String methodName = getTestName();
		final IParser p = ourCtx.newXmlParser();

		final Conformance conf = new Conformance();
		conf.setCopyright("COPY");

		final Patient patient = new Patient();
		patient.addName().addFamily("FAMILY");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				if (myResponseCount++ == 0) {
					return new ReaderInputStream(new StringReader(p.encodeResourceToString(conf)), Charset.forName("UTF-8"));
				} else {
					return new ReaderInputStream(new StringReader(p.encodeResourceToString(patient)), Charset.forName("UTF-8"));
				}
			}
		});

		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.ONCE);
		IGenericClient client = ourCtx.newRestfulGenericClient("http://" + methodName + ".example.com/fhir");

		Patient resp = client.read(Patient.class, new IdDt("123"));
		assertThat(resp.getName().get(0).getFamily().get(0).getValue()).isEqualTo("FAMILY");
		assertThat(capt.getAllValues().get(0).getURI().toASCIIString()).isEqualTo("http://" + methodName + ".example.com/fhir/metadata");
		assertThat(capt.getAllValues().get(0).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(0).getHeaders("Accept")[0].getValue()).contains(Constants.HEADER_ACCEPT_VALUE_XML_OR_JSON_LEGACY);
		assertThat(capt.getAllValues().get(0).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_XML);
		assertThat(capt.getAllValues().get(0).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_JSON);
		assertThat(capt.getAllValues().get(1).getURI().toASCIIString()).isEqualTo("http://" + methodName + ".example.com/fhir/Patient/123");
		assertThat(capt.getAllValues().get(1).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(1).getHeaders("Accept")[0].getValue()).contains(Constants.HEADER_ACCEPT_VALUE_XML_OR_JSON_LEGACY);
		assertThat(capt.getAllValues().get(1).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_XML);
		assertThat(capt.getAllValues().get(1).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_JSON);
	}

	@Test
	public void testAcceptHeaderPreflightConformancePreferJson() throws Exception {
		String methodName = getTestName();
		final IParser p = ourCtx.newXmlParser();

		final Conformance conf = new Conformance();
		conf.setCopyright("COPY");

		final Patient patient = new Patient();
		patient.addName().addFamily("FAMILY");

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				if (myResponseCount++ == 0) {
					return new ReaderInputStream(new StringReader(p.encodeResourceToString(conf)), Charset.forName("UTF-8"));
				} else {
					return new ReaderInputStream(new StringReader(p.encodeResourceToString(patient)), Charset.forName("UTF-8"));
				}
			}
		});

		ourCtx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.ONCE);
		IGenericClient client = ourCtx.newRestfulGenericClient("http://" + methodName + ".example.com/fhir");
		client.setEncoding(EncodingEnum.JSON);

		Patient resp = client.read(Patient.class, new IdDt("123"));
		assertThat(resp.getName().get(0).getFamily().get(0).getValue()).isEqualTo("FAMILY");
		assertThat(capt.getAllValues().get(0).getURI().toASCIIString()).isEqualTo("http://" + methodName + ".example.com/fhir/metadata?_format=json");
		assertThat(capt.getAllValues().get(0).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(0).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_JSON);
		assertThat(capt.getAllValues().get(0).getHeaders("Accept")[0].getValue()).doesNotContain(Constants.CT_FHIR_XML);
		assertThat(capt.getAllValues().get(1).getURI().toASCIIString()).isEqualTo("http://" + methodName + ".example.com/fhir/Patient/123?_format=json");
		assertThat(capt.getAllValues().get(1).getHeaders("Accept").length).isEqualTo(1);
		assertThat(capt.getAllValues().get(1).getHeaders("Accept")[0].getValue()).contains(Constants.CT_FHIR_JSON);
		assertThat(capt.getAllValues().get(1).getHeaders("Accept")[0].getValue()).doesNotContain(Constants.CT_FHIR_XML);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testConformance() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Conformance conf = new Conformance();
		conf.setCopyright("COPY");

		final String respString = p.encodeResourceToString(conf);
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

		int idx = 0;

		//@formatter:off
		Conformance resp = (Conformance) client.fetchConformance().ofType(Conformance.class).execute();

		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/metadata");
		assertThat(resp.getCopyright()).isEqualTo("COPY");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

	}

	@Test
	public void testCreate() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.addName().addFamily("FOOFAMILY");

		client.create().resource(p).encodedXml().execute();

		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		p.setId("123");

		client.create().resource(p).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		String body = extractBody(capt, idx);
		assertThat(body).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(body).doesNotContain("123");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

	}

	@Test
	public void testCreateConditional() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.addName().addFamily("FOOFAMILY");

		client.create().resource(p).conditionalByUrl("Patient?name=foo").encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_IF_NONE_EXIST).getValue()).isEqualTo("http://example.com/fhir/Patient?name=foo");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		client.create().resource(p).conditionalByUrl("Patient?name=http://foo|bar").encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_IF_NONE_EXIST).getValue()).isEqualTo("http://example.com/fhir/Patient?name=http%3A//foo%7Cbar");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		client.create().resource(p).conditional().where(Patient.NAME.matches().value("foo")).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_IF_NONE_EXIST).getValue()).isEqualTo("http://example.com/fhir/Patient?name=foo");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

	}

	@SuppressWarnings("deprecation")
	@Test
	public void testCreateNonFluent() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.addName().addFamily("FOOFAMILY");

		client.create().resource(p).encodedXml().execute();

		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;
	}

	@Test
	public void testCreatePrefer() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.addName().addFamily("FOOFAMILY");

		client.create().resource(p).prefer(PreferReturnEnum.MINIMAL).execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER)[0].getValue()).isEqualTo(Constants.HEADER_PREFER_RETURN + '=' + Constants.HEADER_PREFER_RETURN_MINIMAL);
		idx++;

		client.create().resource(p).prefer(PreferReturnEnum.REPRESENTATION).execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER)[0].getValue()).isEqualTo(Constants.HEADER_PREFER_RETURN + '=' + Constants.HEADER_PREFER_RETURN_REPRESENTATION);
		idx++;

	}

	@Test
	public void testCreateReturningResourceBody() throws Exception {
		Patient p = new Patient();
		p.setId("123");
		final String formatted = ourCtx.newXmlParser().encodeResourceToString(p);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_200_OK, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(formatted), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		p = new Patient();
		p.setId(new IdDt("1"));
		p.addName().addFamily("FOOFAMILY");

		MethodOutcome output = client.create().resource(p).execute();
		assertThat(output.getResource()).isNotNull();
		assertThat(output.getResource().getIdElement().toUnqualifiedVersionless().getValue()).isEqualTo("Patient/123");
	}

	@Test
	public void testDeleteByResource() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient pat = new Patient();
		pat.setId("Patient/123");

		client.delete().resource(pat).execute();
		assertThat(capt.getAllValues().get(idx).getMethod()).isEqualTo("DELETE");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient/123");
	}

	@Test
	public void testDeleteConditional() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		// when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type",
		// Constants.CT_TEXT + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		client.delete().resourceById(new IdDt("Patient/123")).execute();
		assertThat(capt.getAllValues().get(idx).getMethod()).isEqualTo("DELETE");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient/123");
		idx++;

		client.delete().resourceConditionalByUrl("Patient?name=foo").execute();
		assertThat(capt.getAllValues().get(idx).getMethod()).isEqualTo("DELETE");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=foo");
		idx++;

		client.delete().resourceConditionalByType("Patient").where(Patient.NAME.matches().value("foo")).execute();
		assertThat(capt.getAllValues().get(idx).getMethod()).isEqualTo("DELETE");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=foo");
		idx++;

	}

	@Test
	public void testDeleteInvalidRequest() throws Exception {
		Patient pat = new Patient();
		pat.setId("123");

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		try {
			client.delete().resource(pat).execute();
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1369) + "theResource.getId() must contain a resource type and logical ID at a minimum (e.g. Patient/1234), found: 123");
		}

		try {
			client.delete().resourceById(new IdDt("123")).execute();
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1370) + "theId must contain a resource type and logical ID at a minimum (e.g. Patient/1234)found: 123");
		}

		try {
			client.delete().resourceById("", "123").execute();
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("theResourceType can not be blank/null");
		}

		try {
			client.delete().resourceById("Patient", "").execute();
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo("theLogicalId can not be blank/null");
		}

		try {
			client.delete().resourceConditionalByType("InvalidType");
			fail("");		} catch (DataFormatException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1684) + "Unknown resource name \"InvalidType\" (this name is not known in FHIR version \"DSTU2\")");
		}
	}

	/**
	 * See #322
	 */
	@Test
	public void testFetchConformanceWithSmartExtensions() throws Exception {
		final String respString = IOUtils.toString(GenericClientDstu2Test.class.getResourceAsStream("/conformance_322.json"));
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(respString), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://localhost:8080/fhir");
		Conformance conf = client.fetchConformance().ofType(Conformance.class).execute();

		Rest rest = conf.getRest().get(0);
		RestSecurity security = rest.getSecurity();

		List<ExtensionDt> ext = security.getUndeclaredExtensionsByUrl("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
		List<ExtensionDt> tokenExts = ext.get(0).getUndeclaredExtensionsByUrl("token");
		ExtensionDt tokenExt = tokenExts.get(0);
		UriDt value = (UriDt) tokenExt.getValue();
		assertThat(value.getValueAsString()).isEqualTo("https://my-server.org/token");

	}

	/**
	 * See #322
	 */
	@Test
	public void testFetchConformanceWithSmartExtensionsAltCase() throws Exception {
		final String respString = IOUtils.toString(GenericClientDstu2Test.class.getResourceAsStream("/conformance_322.json")).replace("valueuri", "valueUri");
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(respString), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://localhost:8080/fhir");
		Conformance conf = client.fetchConformance().ofType(Conformance.class).execute();

		Rest rest = conf.getRest().get(0);
		RestSecurity security = rest.getSecurity();

		List<ExtensionDt> ext = security.getUndeclaredExtensionsByUrl("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
		List<ExtensionDt> tokenExts = ext.get(0).getUndeclaredExtensionsByUrl("token");
		ExtensionDt tokenExt = tokenExts.get(0);
		UriDt value = (UriDt) tokenExt.getValue();
		assertThat(value.getValueAsString()).isEqualTo("https://my-server.org/token");

	}

	@Test
	public void testHistory() throws Exception {

		final String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;
		ca.uhn.fhir.model.dstu2.resource.Bundle response;

		//@formatter:off
		response = client
			.history()
			.onServer()
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/_history");
		assertThat(response.getEntry()).hasSize(1);
		idx++;

		//@formatter:off
		response = client
			.history()
			.onServer()
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.since((Date) null)
			.count(null)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/_history");
		assertThat(response.getEntry()).hasSize(1);
		idx++;

		//@formatter:off
		response = client
			.history()
			.onServer()
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.since(new InstantDt())
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/_history");
		assertThat(response.getEntry()).hasSize(1);
		idx++;

		//@formatter:off
		response = client
			.history()
			.onType(Patient.class)
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient/_history");
		assertThat(response.getEntry()).hasSize(1);
		idx++;

		//@formatter:off
		response = client
			.history()
			.onInstance(new IdDt("Patient", "123"))
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient/123/_history");
		assertThat(response.getEntry()).hasSize(1);
		idx++;

		//@formatter:off
		response = client
			.history()
			.onInstance(new IdDt("Patient", "123"))
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.count(123)
			.since(new InstantDt("2001-01-02T11:22:33Z"))
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString(), either(equalTo("http://example.com/fhir/Patient/123/_history?_since=2001-01-02T11:22:33Z&_count=123"))
			.or(equalTo("http://example.com/fhir/Patient/123/_history?_count=123&_since=2001-01-02T11:22:33Z")));
		assertThat(response.getEntry()).hasSize(1);
		idx++;

		//@formatter:off
		response = client
			.history()
			.onInstance(new IdDt("Patient", "123"))
			.andReturnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.since(new InstantDt("2001-01-02T11:22:33Z").getValue())
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).contains("_since=2001-01");
		assertThat(response.getEntry()).hasSize(1);
		idx++;
	}

	@Test
	public void testInvalidClient() {
		try {
			ourCtx.getRestfulClientFactory().newClient(RestfulClientInstance.class, "http://foo");
			fail("");		} catch (ConfigurationException e) {
			assertThat(e.toString()).isEqualTo("ca.uhn.fhir.context.ConfigurationException: " + Msg.code(1354) + "ca.uhn.fhir.rest.client.GenericClientDstu2Test.RestfulClientInstance is not an interface");
		}
	}

	@Test
	public void testMetaAdd() throws Exception {
		IParser p = ourCtx.newXmlParser();

		MetaDt inMeta = new MetaDt().addProfile("urn:profile:in");

		Parameters outParams = new Parameters();
		outParams.addParameter().setName("meta").setValue(new MetaDt().addProfile("urn:profile:out"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		MetaDt resp = client
			.meta()
			.add()
			.onResource(new IdDt("Patient/123"))
			.meta(inMeta)
			.encodedXml()
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$meta-add");
		assertThat(resp.getProfile().get(0).getValue()).isEqualTo("urn:profile:out");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(extractBody(capt, idx)).isEqualTo("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"meta\"/><valueMeta><profile value=\"urn:profile:in\"/></valueMeta></parameter></Parameters>");
		idx++;

	}

	@Test
	public void testMetaGet() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters inParams = new Parameters();
		inParams.addParameter().setName("meta").setValue(new MetaDt().addProfile("urn:profile:in"));

		Parameters outParams = new Parameters();
		outParams.addParameter().setName("meta").setValue(new MetaDt().addProfile("urn:profile:out"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		MetaDt resp = client
			.meta()
			.get(MetaDt.class)
			.fromServer()
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$meta");
		assertThat(resp.getProfile().get(0).getValue()).isEqualTo("urn:profile:out");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		//@formatter:off
		resp = client
			.meta()
			.get(MetaDt.class)
			.fromType("Patient")
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$meta");
		assertThat(resp.getProfile().get(0).getValue()).isEqualTo("urn:profile:out");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		//@formatter:off
		resp = client
			.meta()
			.get(MetaDt.class)
			.fromResource(new IdDt("Patient/123"))
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$meta");
		assertThat(resp.getProfile().get(0).getValue()).isEqualTo("urn:profile:out");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

	}

	@Test
	public void testOperationAsGetWithInParameters() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters inParams = new Parameters();
		inParams.addParameter().setName("param1").setValue(new StringDt("STRINGVALIN1"));
		inParams.addParameter().setName("param1").setValue(new StringDt("STRINGVALIN1b"));
		inParams.addParameter().setName("param2").setValue(new StringDt("STRINGVALIN2"));

		Parameters outParams = new Parameters();
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT1"));
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT2"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		Parameters resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.useHttpGet()
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION?param1=STRINGVALIN1&param1=STRINGVALIN1b&param2=STRINGVALIN2");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onType(Patient.class)
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.useHttpGet()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$SOMEOPERATION?param1=STRINGVALIN1&param1=STRINGVALIN1b&param2=STRINGVALIN2");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("Patient", "123"))
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.useHttpGet()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION?param1=STRINGVALIN1&param1=STRINGVALIN1b&param2=STRINGVALIN2");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		// @formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("http://foo.com/bar/baz/Patient/123/_history/22"))
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.useHttpGet()
			.execute();
		// @formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION?param1=STRINGVALIN1&param1=STRINGVALIN1b&param2=STRINGVALIN2");
		idx++;
	}

	@Test
	public void testOperationAsGetWithNoInParameters() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters outParams = new Parameters();
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT1"));
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT2"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		Parameters resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.useHttpGet()
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onType(Patient.class)
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.useHttpGet()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("Patient", "123"))
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.useHttpGet()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("GET");
		idx++;

		// @formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("http://foo.com/bar/baz/Patient/123/_history/22"))
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.useHttpGet()
			.execute();
		// @formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION");
		idx++;
	}

	@Test
	public void testOperationWithBundleResponseJson() throws Exception {

		final String resp = "{\n" + "    \"resourceType\":\"Bundle\",\n" + "    \"id\":\"8cef5f2a-0ba9-43a5-be26-c8dde9ff0e19\",\n" + "    \"base\":\"http://fhirtest.uhn.ca/baseDstu2\"\n" + "}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(resp), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://fhirtest.uhn.ca/baseDstu2");

		client.registerInterceptor(new LoggingInterceptor(true));

		// Create the input parameters to pass to the server
		Parameters inParams = new Parameters();
		inParams.addParameter().setName("start").setValue(new DateDt("2001-01-01"));
		inParams.addParameter().setName("end").setValue(new DateDt("2015-03-01"));

		// Invoke $everything on "Patient/1"
		Parameters outParams = client.operation().onInstance(new IdDt("Patient", "18066")).named("$everything").withParameters(inParams).execute();

		/*
		 * Note that the $everything operation returns a Bundle instead of a Parameters resource. The client operation
		 * methods return a Parameters instance however, so HAPI creates a Parameters object
		 * with a single parameter containing the value.
		 */
		ca.uhn.fhir.model.dstu2.resource.Bundle responseBundle = (ca.uhn.fhir.model.dstu2.resource.Bundle) outParams.getParameter().get(0).getResource();

		// Print the response bundle
		assertThat(responseBundle.getId().getIdPart()).isEqualTo("8cef5f2a-0ba9-43a5-be26-c8dde9ff0e19");
	}

	@Test
	public void testOperationWithBundleResponseXml() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters inParams = new Parameters();
		inParams.addParameter().setValue(new StringDt("STRINGVALIN1"));
		inParams.addParameter().setValue(new StringDt("STRINGVALIN2"));
		String reqString = p.encodeResourceToString(inParams);

		ca.uhn.fhir.model.dstu2.resource.Bundle outParams = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		outParams.setTotal(123);
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		Parameters resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.encodedXml()
			.execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(resp.getParameter()).hasSize(1);
		assertThat(resp.getParameter().get(0).getResource().getClass()).isEqualTo(ca.uhn.fhir.model.dstu2.resource.Bundle.class);
		idx++;
	}

	@Test
	public void testOperationWithInlineParams() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters outParams = new Parameters();
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT1"));
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT2"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		Parameters resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withParameter(Parameters.class, "name1", new StringDt("value1"))
			.andParameter("name2", new StringDt("value1"))
			.encodedXml()
			.execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat((extractBody(capt, idx))).isEqualTo("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"name1\"/><valueString value=\"value1\"/></parameter><parameter><name value=\"name2\"/><valueString value=\"value1\"/></parameter></Parameters>");
		idx++;

		/*
		 * Composite type
		 */

		//@formatter:off
		resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withParameter(Parameters.class, "name1", new IdentifierDt("system1", "value1"))
			.andParameter("name2", new StringDt("value1"))
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.JSON.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat((extractBody(capt, idx))).isEqualTo("{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"name1\",\"valueIdentifier\":{\"system\":\"system1\",\"value\":\"value1\"}},{\"name\":\"name2\",\"valueString\":\"value1\"}]}");
		idx++;

		/*
		 * Resource
		 */

		//@formatter:off
		resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withParameter(Parameters.class, "name1", new IdentifierDt("system1", "value1"))
			.andParameter("name2", new Patient().setActive(true))
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.JSON.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat((extractBody(capt, idx))).isEqualTo("{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"name1\",\"valueIdentifier\":{\"system\":\"system1\",\"value\":\"value1\"}},{\"name\":\"name2\",\"resource\":{\"resourceType\":\"Patient\",\"active\":true}}]}");
		idx++;

	}

	@Test
	public void testOperationWithInvalidParam() {
		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		// Who knows what the heck this is!
		IBase weirdBase = new IBase() {
			private static final long serialVersionUID = 1L;

			@Override
			public List<String> getFormatCommentsPost() {
				return null;
			}

			@Override
			public Object getUserData(String theName) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void setUserData(String theName, Object theValue) {
				throw new UnsupportedOperationException();
			}

			@Override
			public List<String> getFormatCommentsPre() {
				return null;
			}

			@Override
			public boolean hasFormatComment() {
				return false;
			}

			@Override
			public boolean isEmpty() {
				return false;
			}
		};

		try {
			client
				.operation()
				.onServer()
				.named("$SOMEOPERATION")
				.withParameter(Parameters.class, "name1", weirdBase)
				.execute();
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1380) + "Don't know how to handle parameter of type class ca.uhn.fhir.rest.client.GenericClientDstu2Test$22");
		}
	}

	@Test
	public void testOperationWithListOfParameterResponse() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters inParams = new Parameters();
		inParams.addParameter().setValue(new StringDt("STRINGVALIN1"));
		inParams.addParameter().setValue(new StringDt("STRINGVALIN2"));
		String reqString = p.encodeResourceToString(inParams);

		Parameters outParams = new Parameters();
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT1"));
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT2"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		Parameters resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.encodedXml()
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onType(Patient.class)
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.encodedXml()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("Patient", "123"))
			.named("$SOMEOPERATION")
			.withParameters(inParams)
			.encodedXml()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		resp = client.operation().onInstance(new IdDt("http://foo.com/bar/baz/Patient/123/_history/22")).named("$SOMEOPERATION").withParameters(inParams).execute();
		// @formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION");
		idx++;
	}

	@Test
	public void testOperationWithNoInParameters() throws Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters inParams = new Parameters();
		final String reqString = p.encodeResourceToString(inParams);

		Parameters outParams = new Parameters();
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT1"));
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT2"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		Parameters resp = client
			.operation()
			.onServer()
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.encodedXml()
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onType(Patient.class)
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.encodedXml()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		//@formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("Patient", "123"))
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.encodedXml()
			.execute();
		//@formatter:on		
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION");
		assertThat(p.encodeResourceToString(resp)).isEqualTo(respString);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(reqString).isEqualTo(extractBody(capt, idx));
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		idx++;

		// @formatter:off
		resp = client
			.operation()
			.onInstance(new IdDt("http://foo.com/bar/baz/Patient/123/_history/22"))
			.named("$SOMEOPERATION")
			.withNoParameters(Parameters.class)
			.encodedXml()
			.execute();
		// @formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/123/$SOMEOPERATION");
		idx++;
	}

	@Test
	public void testOperationWithProfiledDatatypeParam() throws IOException, Exception {
		IParser p = ourCtx.newXmlParser();

		Parameters outParams = new Parameters();
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT1"));
		outParams.addParameter().setValue(new StringDt("STRINGVALOUT2"));
		final String respString = p.encodeResourceToString(outParams);

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

		int idx = 0;

		//@formatter:off
		client
			.operation()
			.onInstance(new IdDt("http://foo/Patient/1"))
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeDt("8495-4"))
			.andParameter("system", new UriDt("http://loinc.org"))
			.useHttpGet()
			.execute();
		//@formatter:off

		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/1/$validate-code?code=8495-4&system=http%3A%2F%2Floinc.org");

		//@formatter:off
		idx++;
		client
			.operation()
			.onInstance(new IdDt("http://foo/Patient/1"))
			.named("validate-code")
			.withParameter(Parameters.class, "code", new CodeDt("8495-4"))
			.andParameter("system", new UriDt("http://loinc.org"))
			.encodedXml()
			.execute();
		//@formatter:off

		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/1/$validate-code");
		ourLog.info(extractBody(capt, idx));
		assertThat(extractBody(capt, idx)).isEqualTo("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"code\"/><valueCode value=\"8495-4\"/></parameter><parameter><name value=\"system\"/><valueUri value=\"http://loinc.org\"/></parameter></Parameters>");

	}

	@Test
	public void testPageNext() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(getPatientFeedWithOneResult()), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		ca.uhn.fhir.model.dstu2.resource.Bundle sourceBundle = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		sourceBundle.getLinkOrCreate(IBaseBundle.LINK_PREV).setUrl("http://foo.bar/prev");
		sourceBundle.getLinkOrCreate(IBaseBundle.LINK_NEXT).setUrl("http://foo.bar/next");

		//@formatter:off
		ca.uhn.fhir.model.dstu2.resource.Bundle resp = client
			.loadPage()
			.next(sourceBundle)
			.execute();
		//@formatter:on

		assertThat(resp.getEntry()).hasSize(1);
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://foo.bar/next");
		idx++;

	}

	@Test
	public void testPageNextNoLink() throws Exception {
		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		ca.uhn.fhir.model.dstu2.resource.Bundle sourceBundle = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		try {
			client.loadPage().next(sourceBundle).execute();
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).contains("Can not perform paging operation because no link was found in Bundle with relation \"next\"");
		}
	}

	@Test
	public void testPagePrev() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(getPatientFeedWithOneResult()), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		ca.uhn.fhir.model.dstu2.resource.Bundle sourceBundle = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		sourceBundle.getLinkOrCreate("previous").setUrl("http://foo.bar/prev");

		//@formatter:off
		ca.uhn.fhir.model.dstu2.resource.Bundle resp = client
			.loadPage()
			.previous(sourceBundle)
			.execute();
		//@formatter:on

		assertThat(resp.getEntry()).hasSize(1);
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://foo.bar/prev");
		idx++;

		/*
		 * Try with "prev" instead of "previous"
		 */

		sourceBundle = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		sourceBundle.getLinkOrCreate("prev").setUrl("http://foo.bar/prev");

		//@formatter:off
		resp = client
			.loadPage()
			.previous(sourceBundle)
			.execute();
		//@formatter:on

		assertThat(resp.getEntry()).hasSize(1);
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://foo.bar/prev");
		idx++;

	}

	@Test
	public void testProviderWhereWeForgotToSetTheContext() throws Exception {
		ApacheRestfulClientFactory clientFactory = new ApacheRestfulClientFactory(); // no ctx
		clientFactory.setServerValidationMode(ServerValidationModeEnum.NEVER);

		ourCtx.setRestfulClientFactory(clientFactory);

		try {
			ourCtx.newRestfulGenericClient("http://localhost:8080/fhir");
			fail("");		} catch (IllegalStateException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1355) + "ApacheRestfulClientFactory does not have FhirContext defined. This must be set via ApacheRestfulClientFactory#setFhirContext(FhirContext)");
		}
	}

	@Test
	public void testReadByUri() throws Exception {

		Patient patient = new Patient();
		patient.addName().addFamily("FAM");
		final String input = ourCtx.newXmlParser().encodeResourceToString(patient);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getAllHeaders()).thenReturn(new Header[]{new BasicHeader(Constants.HEADER_LAST_MODIFIED, "Sat, 20 Jun 2015 19:32:17 GMT")});
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(input), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Patient response;

		int idx = 0;
		response = (Patient) client.read(new UriDt("http://domain2.example.com/base/Patient/123"));
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://domain2.example.com/base/Patient/123");
		assertThat(response.getName().get(0).getFamily().get(0).getValue()).isEqualTo("FAM");
	}

	@Test
	public void testReadFluentByUri() throws Exception {

		Patient patient = new Patient();
		patient.addName().addFamily("FAM");
		final String input = ourCtx.newXmlParser().encodeResourceToString(patient);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getAllHeaders()).thenReturn(new Header[]{new BasicHeader(Constants.HEADER_LAST_MODIFIED, "Sat, 20 Jun 2015 19:32:17 GMT")});
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(input), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Patient response;

		int idx = 0;
		response = (Patient) client.read().resource(Patient.class).withUrl(new IdDt("http://domain2.example.com/base/Patient/123")).execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://domain2.example.com/base/Patient/123");
		assertThat(response.getName().get(0).getFamily().get(0).getValue()).isEqualTo("FAM");
	}

	@Test
	public void testReadForUnknownType() throws Exception {
		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");
		try {
			client.read(new UriDt("1"));
			fail("");		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1365) + "The given URI is not an absolute URL and is not usable for this operation: 1");
		}

		try {
			client.read(new UriDt("http://example.com/InvalidResource/1"));
			fail("");		} catch (DataFormatException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1684) + "Unknown resource name \"InvalidResource\" (this name is not known in FHIR version \"DSTU2\")");
		}
	}

	@Test
	public void testReadUpdatedHeaderDoesntOverwriteResourceValue() throws Exception {

		//@formatter:off
		final String input = "<Bundle xmlns=\"http://hl7.org/fhir\">\n" +
			"   <id value=\"e2ee823b-ee4d-472d-b79d-495c23f16b99\"/>\n" +
			"   <meta>\n" +
			"      <lastUpdated value=\"2015-06-22T15:48:57.554-04:00\"/>\n" +
			"   </meta>\n" +
			"   <type value=\"searchset\"/>\n" +
			"   <base value=\"http://localhost:58109/fhir/context\"/>\n" +
			"   <total value=\"0\"/>\n" +
			"   <link>\n" +
			"      <relation value=\"self\"/>\n" +
			"      <url value=\"http://localhost:58109/fhir/context/Patient?_pretty=true\"/>\n" +
			"   </link>\n" +
			"</Bundle>";
		//@formatter:on

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getAllHeaders()).thenReturn(new Header[]{new BasicHeader(Constants.HEADER_LAST_MODIFIED, "Sat, 20 Jun 2015 19:32:17 GMT")});
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(input), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		ca.uhn.fhir.model.dstu2.resource.Bundle response;

		//@formatter:off
		response = client
			.search()
			.forResource(Patient.class)
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on

		assertThat(ResourceMetadataKeyEnum.UPDATED.get(response).getValueAsString()).isEqualTo("2015-06-22T15:48:57.554-04:00");
	}

	@Test
	public void testReadWithElementsParam() throws Exception {
		String msg = "{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		//@formatter:off
		IBaseResource response = client.read()
			.resource("Patient")
			.withId("123")
			.elementsSubset("name", "identifier")
			.execute();
		//@formatter:on

		assertThat(capt.getValue().getURI().toString(),
			either(equalTo("http://example.com/fhir/Patient/123?_elements=name%2Cidentifier")).or(equalTo("http://example.com/fhir/Patient/123?_elements=identifier%2Cname")));
		assertThat(response.getClass()).isEqualTo(Patient.class);

	}

	@Test
	public void testReadWithSummaryInvalid() throws Exception {
		String msg = "<>>>><<<<>";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_HTML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		//@formatter:off
		try {
			client.read()
				.resource(Patient.class)
				.withId("123")
				.summaryMode(SummaryEnum.TEXT)
				.execute();
			fail("");		} catch (InvalidResponseException e) {
			assertThat(e.getMessage()).contains("String does not appear to be valid");
		}
		//@formatter:on
	}

	@Test
	public void testReadWithSummaryParamHtml() throws Exception {
		String msg = "<div>HELP IM A DIV</div>";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_HTML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		//@formatter:off
		Patient response = client.read()
			.resource(Patient.class)
			.withId("123")
			.summaryMode(SummaryEnum.TEXT)
			.execute();
		//@formatter:on

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient/123?_summary=text");
		assertThat(response.getClass()).isEqualTo(Patient.class);
		assertThat(response.getText().getDiv().getValueAsString()).isEqualTo("<div xmlns=\"http://www.w3.org/1999/xhtml\">HELP IM A DIV</div>");

	}

	@Test
	public void testSearchByNumber() throws Exception {
		final String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");
		int idx = 0;

		//@formatter:off
		client.search()
			.forResource("Encounter")
			.where(Encounter.LENGTH.greaterThan().number(123))
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Encounter?length=gt123");
		idx++;

		//@formatter:off
		client.search()
			.forResource("Encounter")
			.where(Encounter.LENGTH.lessThan().number(123))
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Encounter?length=lt123");
		idx++;

		//@formatter:off
		client.search()
			.forResource("Encounter")
			.where(Encounter.LENGTH.greaterThanOrEqual().number("123"))
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Encounter?length=ge123");
		idx++;

		//@formatter:off
		client.search()
			.forResource("Encounter")
			.where(Encounter.LENGTH.lessThanOrEqual().number("123"))
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Encounter?length=le123");
		idx++;

		//@formatter:off
		client.search()
			.forResource("Encounter")
			.where(Encounter.LENGTH.exactly().number(123))
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Encounter?length=123");
		idx++;

	}

	@Test
	public void testSearchByPost() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.where(Patient.NAME.matches().value("james"))
			.elementsSubset("name", "identifier")
			.usingStyle(SearchStyleEnum.POST)
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient/_search?_elements=identifier%2Cname");

		// assertThat(capt.getValue().getURI().toString(),
		// either(equalTo("http://example.com/fhir/Patient?name=james&_elements=name%2Cidentifier")).or(equalTo("http://example.com/fhir/Patient?name=james&_elements=identifier%2Cname")));

		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

		ourLog.info(Arrays.asList(capt.getValue().getAllHeaders()).toString());
		ourLog.info(capt.getValue().toString());

		HttpEntityEnclosingRequestBase v = (HttpEntityEnclosingRequestBase) capt.getValue();
		String req = IOUtils.toString(v.getEntity().getContent(), "UTF-8");
		assertThat(req).isEqualTo("name=james");

		assertThat(v.getEntity().getContentType().getValue().replace(" ", "").toLowerCase()).isEqualTo("application/x-www-form-urlencoded;charset=utf-8");
		assertThat(capt.getValue().getFirstHeader("accept").getValue()).isEqualTo(Constants.HEADER_ACCEPT_VALUE_XML_OR_JSON_LEGACY);
		assertThat(capt.getValue().getFirstHeader("user-agent").getValue()).isNotEmpty();
	}

	@Test
	public void testSearchByPostUseJson() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.where(Patient.NAME.matches().value("james"))
			.elementsSubset("name", "identifier")
			.usingStyle(SearchStyleEnum.POST)
			.encodedJson()
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).contains("http://example.com/fhir/Patient/_search?");
		assertThat(capt.getValue().getURI().toString()).contains("_elements=identifier%2Cname");
		assertThat(capt.getValue().getURI().toString()).doesNotContain("_format=json");

		// assertThat(capt.getValue().getURI().toString(),
		// either(equalTo("http://example.com/fhir/Patient?name=james&_elements=name%2Cidentifier")).or(equalTo("http://example.com/fhir/Patient?name=james&_elements=identifier%2Cname")));

		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

		ourLog.info(Arrays.asList(capt.getValue().getAllHeaders()).toString());
		ourLog.info(capt.getValue().toString());

		HttpEntityEnclosingRequestBase v = (HttpEntityEnclosingRequestBase) capt.getValue();
		String req = IOUtils.toString(v.getEntity().getContent(), "UTF-8");
		assertThat(req).isEqualTo("name=james");

		assertThat(v.getEntity().getContentType().getValue().replace(" ", "").toLowerCase()).isEqualTo("application/x-www-form-urlencoded;charset=utf-8");
		assertThat(capt.getValue().getFirstHeader("accept").getValue()).isEqualTo(Constants.CT_FHIR_JSON);
	}

	@Test
	public void testSearchByString() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.where(Patient.NAME.matches().value("james"))
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=james");
		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

	}

	@Test
	public void testSearchByUrl() throws Exception {

		final String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");
		int idx = 0;

		//@formatter:off
		ca.uhn.fhir.model.dstu2.resource.Bundle response = client.search()
			.byUrl("http://foo?name=http://foo|bar")
			.encodedJson()
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://foo?name=http%3A//foo%7Cbar&_format=json");
		assertThat(response).isNotNull();
		idx++;

		//@formatter:off
		response = client.search()
			.byUrl("Patient?name=http://foo|bar")
			.encodedJson()
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=http%3A//foo%7Cbar&_format=json");
		assertThat(response).isNotNull();
		idx++;

		//@formatter:off
		response = client.search()
			.byUrl("/Patient?name=http://foo|bar")
			.encodedJson()
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=http%3A//foo%7Cbar&_format=json");
		assertThat(response).isNotNull();
		idx++;

		//@formatter:off
		response = client.search()
			.byUrl("Patient")
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient");
		assertThat(response).isNotNull();
		idx++;

		//@formatter:off
		response = client.search()
			.byUrl("Patient?")
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?");
		assertThat(response).isNotNull();
		idx++;

		try {
			client.search().byUrl("foo/bar?test=1");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(1393) + "Search URL must be either a complete URL starting with http: or https:, or a relative FHIR URL in the form [ResourceType]?[Params]");
		}
	}

	/**
	 * See #191
	 */
	@Test
	public void testSearchReturningDstu2Bundle() throws Exception {
		String msg = IOUtils.toString(XmlParserDstu2Test.class.getResourceAsStream("/bundle_orion.xml"));
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		//@formatter:off
		ca.uhn.fhir.model.dstu2.resource.Bundle response = client.search()
			.forResource("Observation")
			.where(Patient.NAME.matches().value("FOO"))
			.returnBundle(ca.uhn.fhir.model.dstu2.resource.Bundle.class)
			.execute();
		//@formatter:on

		Link link = response.getLink().get(0);
		assertThat(link.getRelation()).isEqualTo("just trying add link");
		assertThat(link.getUrl()).isEqualTo("blarion");

		Entry entry = response.getEntry().get(0);
		link = entry.getLink().get(0);
		assertThat(link.getRelation()).isEqualTo("orionhealth.edit");
		assertThat(link.getUrl()).isEqualTo("Observation");
	}

	@Test
	public void testSearchWithElementsParam() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.where(Patient.NAME.matches().value("james"))
			.elementsSubset("name", "identifier")
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString(),
			either(equalTo("http://example.com/fhir/Patient?name=james&_elements=name%2Cidentifier")).or(equalTo("http://example.com/fhir/Patient?name=james&_elements=identifier%2Cname")));
		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

	}

	@Test
	public void testSearchWithLastUpdated() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.where(Patient.NAME.matches().value("james"))
			.lastUpdated(new DateRangeParam("2011-01-01", "2012-01-01"))
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=james&_lastUpdated=ge2011-01-01&_lastUpdated=le2012-01-01");
		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

	}

	@Test
	public void testSearchWithMap() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		HashMap<String, List<IQueryParameterType>> params = new HashMap<String, List<IQueryParameterType>>();
		params.put("foo", Arrays.asList((IQueryParameterType) new DateParam("2001")));
		Bundle response = client
			.search()
			.forResource(Patient.class)
			.where(params)
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient?foo=2001");
		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

	}

	@Test
	public void testSearchWithProfileAndSecurity() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.withProfile("http://foo1")
			.withProfile("http://foo2")
			.withSecurity("system1", "code1")
			.withSecurity("system2", "code2")
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient?_security=system1%7Ccode1&_security=system2%7Ccode2&_profile=http%3A%2F%2Ffoo1&_profile=http%3A%2F%2Ffoo2");
		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

	}

	@SuppressWarnings("unused")
	@Test
	public void testSearchWithReverseInclude() throws Exception {

		String msg = getPatientFeedWithOneResult();

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource(Patient.class)
			.encodedJson()
			.revInclude(new Include("Provenance:target"))
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient?_revinclude=Provenance%3Atarget&_format=json");

	}

	@Test
	public void testSearchWithSummaryParam() throws Exception {
		String msg = "{\"resourceType\":\"Bundle\",\"id\":null,\"base\":\"http://localhost:57931/fhir/contextDev\",\"total\":1,\"link\":[{\"relation\":\"self\",\"url\":\"http://localhost:57931/fhir/contextDev/Patient?identifier=urn%3AMultiFhirVersionTest%7CtestSubmitPatient01&_format=json\"}],\"entry\":[{\"resource\":{\"resourceType\":\"Patient\",\"id\":\"1\",\"meta\":{\"versionId\":\"1\",\"lastUpdated\":\"2014-12-20T18:41:29.706-05:00\"},\"identifier\":[{\"system\":\"urn:MultiFhirVersionTest\",\"value\":\"testSubmitPatient01\"}]}}]}";

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Bundle response = client.search()
			.forResource("Patient")
			.where(Patient.NAME.matches().value("james"))
			.summaryMode(SummaryEnum.FALSE)
			.returnBundle(Bundle.class)
			.execute();

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=james&_summary=false");
		assertThat(response.getEntry().get(0).getResource().getClass()).isEqualTo(Patient.class);

	}

	@Test
	public void testTransactionWithListOfResources() throws Exception {

		ca.uhn.fhir.model.dstu2.resource.Bundle resp = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		resp.addEntry().getResponse().setLocation("Patient/1/_history/1");
		resp.addEntry().getResponse().setLocation("Patient/2/_history/2");
		String respString = ourCtx.newJsonParser().encodeResourceToString(resp);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(respString), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		List<IBaseResource> input = new ArrayList<IBaseResource>();

		Patient p1 = new Patient(); // No ID
		p1.addName().addFamily("PATIENT1");
		input.add(p1);

		Patient p2 = new Patient(); // Yes ID
		p2.addName().addFamily("PATIENT2");
		p2.setId("http://foo.com/Patient/2");
		input.add(p2);

		//@formatter:off
		List<IBaseResource> response = client.transaction()
			.withResources(input)
			.encodedJson()
			.prettyPrint()
			.execute();
		//@formatter:on

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir?_pretty=true");
		assertThat(response).hasSize(2);

		String requestString = IOUtils.toString(((HttpEntityEnclosingRequest) capt.getValue()).getEntity().getContent());
		ourLog.info(requestString);
		ca.uhn.fhir.model.dstu2.resource.Bundle requestBundle = ourCtx.newJsonParser().parseResource(ca.uhn.fhir.model.dstu2.resource.Bundle.class, requestString);
		assertThat(requestBundle.getEntry()).hasSize(2);
		assertThat(requestBundle.getEntry().get(0).getRequest().getMethod()).isEqualTo("POST");
		assertThat(requestBundle.getEntry().get(1).getRequest().getMethod()).isEqualTo("PUT");
		assertThat(requestBundle.getEntry().get(1).getFullUrl()).isEqualTo("http://foo.com/Patient/2");
		assertThat(capt.getAllValues().get(0).getFirstHeader("content-type").getValue().replaceAll(";.*", "")).isEqualTo("application/json+fhir");

		p1 = (Patient) response.get(0);
		assertThat(p1.getId().toUnqualified()).isEqualTo(new IdDt("Patient/1/_history/1"));
		// assertEquals("PATIENT1", p1.getName().get(0).getFamily().get(0).getValue());

		p2 = (Patient) response.get(1);
		assertThat(p2.getId().toUnqualified()).isEqualTo(new IdDt("Patient/2/_history/2"));
		// assertEquals("PATIENT2", p2.getName().get(0).getFamily().get(0).getValue());
	}

	@Test
	public void testTransactionWithString() throws Exception {

		ca.uhn.fhir.model.dstu2.resource.Bundle req = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		req.addEntry().setResource(new Patient());
		req.addEntry().setResource(new Observation());
		String reqStringJson = ourCtx.newJsonParser().encodeResourceToString(req);
		String reqStringXml = ourCtx.newXmlParser().encodeResourceToString(req);

		ca.uhn.fhir.model.dstu2.resource.Bundle resp = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		resp.addEntry().getResponse().setLocation("Patient/1/_history/1");
		resp.addEntry().getResponse().setLocation("Patient/2/_history/2");
		final String respStringJson = ourCtx.newJsonParser().encodeResourceToString(resp);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(respStringJson), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		//@formatter:off
		String response = client.transaction()
			.withBundle(reqStringJson)
			.execute();
		//@formatter:on

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/");
		assertThat(response).isEqualTo(respStringJson);
		String requestString = IOUtils.toString(((HttpEntityEnclosingRequest) capt.getValue()).getEntity().getContent());
		IOUtils.closeQuietly(((HttpEntityEnclosingRequest) capt.getValue()).getEntity().getContent());
		assertThat(requestString).isEqualTo(reqStringJson);
		assertThat(capt.getValue().getFirstHeader("Content-Type").getValue()).isEqualTo("application/json+fhir; charset=UTF-8");

		//@formatter:off
		response = client.transaction()
			.withBundle(reqStringJson)
			.encodedXml()
			.execute();
		//@formatter:on

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir/");
		assertThat(response).isEqualTo(respStringJson);
		requestString = IOUtils.toString(((HttpEntityEnclosingRequest) capt.getValue()).getEntity().getContent());
		IOUtils.closeQuietly(((HttpEntityEnclosingRequest) capt.getValue()).getEntity().getContent());
		assertThat(requestString).isEqualTo(reqStringXml);
		assertThat(capt.getValue().getFirstHeader("Content-Type").getValue()).isEqualTo("application/xml+fhir; charset=UTF-8");

	}

	@Test
	public void testTransactionWithTransactionResource() throws Exception {

		ca.uhn.fhir.model.dstu2.resource.Bundle resp = new ca.uhn.fhir.model.dstu2.resource.Bundle();
		resp.addEntry().getResponse().setLocation("Patient/1/_history/1");
		resp.addEntry().getResponse().setLocation("Patient/2/_history/2");
		String respString = ourCtx.newJsonParser().encodeResourceToString(resp);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_JSON + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenReturn(new ReaderInputStream(new StringReader(respString), Charset.forName("UTF-8")));

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		ca.uhn.fhir.model.dstu2.resource.Bundle input = new ca.uhn.fhir.model.dstu2.resource.Bundle();

		Patient p1 = new Patient(); // No ID
		p1.addName().addFamily("PATIENT1");
		input.addEntry().setResource(p1);

		Patient p2 = new Patient(); // Yes ID
		p2.addName().addFamily("PATIENT2");
		p2.setId("Patient/2");
		input.addEntry().setResource(p2);

		//@formatter:off
		ca.uhn.fhir.model.dstu2.resource.Bundle response = client.transaction()
			.withBundle(input)
			.encodedJson()
			.execute();
		//@formatter:on

		assertThat(capt.getValue().getURI().toString()).isEqualTo("http://example.com/fhir");
		assertThat(response.getEntry()).hasSize(2);

		assertThat(response.getEntry().get(0).getResponse().getLocation()).isEqualTo("Patient/1/_history/1");
		assertThat(response.getEntry().get(1).getResponse().getLocation()).isEqualTo("Patient/2/_history/2");
	}

	@Test
	public void testUpdateConditional() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.addName().addFamily("FOOFAMILY");

		client.update().resource(p).conditionalByUrl("Patient?name=foo").encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=foo");
		idx++;

		client.update().resource(p).conditionalByUrl("Patient?name=http://foo|bar").encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=http%3A//foo%7Cbar");
		idx++;

		client.update().resource(ourCtx.newXmlParser().encodeResourceToString(p)).conditionalByUrl("Patient?name=foo").encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=foo");
		idx++;

		client.update().resource(p).conditional().where(Patient.NAME.matches().value("foo")).and(Patient.ADDRESS.matches().value("AAA|BBB")).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=foo&address=AAA%5C%7CBBB");
		idx++;

		client.update().resource(ourCtx.newXmlParser().encodeResourceToString(p)).conditional().where(Patient.NAME.matches().value("foo")).and(Patient.ADDRESS.matches().value("AAA|BBB")).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.XML.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("<family value=\"FOOFAMILY\"/>");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient?name=foo&address=AAA%5C%7CBBB");
		idx++;

	}

	@Test
	public void testUpdateNonFluent() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.addName().addFamily("FOOFAMILY");

		client.update(new IdDt("Patient/123"), p);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.JSON.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("{\"family\":[\"FOOFAMILY\"]}");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient/123");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		idx++;

		client.update("123", p);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_CONTENT_TYPE).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getFirstHeader(Constants.HEADER_CONTENT_TYPE).getValue()).isEqualTo(EncodingEnum.JSON.getResourceContentType() + Constants.HEADER_SUFFIX_CT_UTF_8);
		assertThat(extractBody(capt, idx)).contains("{\"family\":[\"FOOFAMILY\"]}");
		assertThat(capt.getAllValues().get(idx).getURI().toString()).isEqualTo("http://example.com/fhir/Patient/123");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("PUT");
		idx++;
	}

	@Test
	public void testUpdatePrefer() throws Exception {
		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_204_NO_CONTENT, ""));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(""), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		int idx = 0;

		Patient p = new Patient();
		p.setId(new IdDt("1"));
		p.addName().addFamily("FOOFAMILY");

		client.update().resource(p).prefer(PreferReturnEnum.MINIMAL).execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER)[0].getValue()).isEqualTo(Constants.HEADER_PREFER_RETURN + '=' + Constants.HEADER_PREFER_RETURN_MINIMAL);
		idx++;

		client.update().resource(p).prefer(PreferReturnEnum.REPRESENTATION).execute();
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER).length).isEqualTo(1);
		assertThat(capt.getAllValues().get(idx).getHeaders(Constants.HEADER_PREFER)[0].getValue()).isEqualTo(Constants.HEADER_PREFER_RETURN + '=' + Constants.HEADER_PREFER_RETURN_REPRESENTATION);
		idx++;

	}

	@Test
	public void testUpdateReturningResourceBody() throws Exception {
		Patient p = new Patient();
		p.setId("123");
		final String formatted = ourCtx.newXmlParser().encodeResourceToString(p);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), Constants.STATUS_HTTP_200_OK, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).then(new Answer<ReaderInputStream>() {
			@Override
			public ReaderInputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(formatted), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		p = new Patient();
		p.setId(new IdDt("1"));
		p.addName().addFamily("FOOFAMILY");

		MethodOutcome output = client.update().resource(p).execute();
		assertThat(output.getResource()).isNotNull();
		assertThat(output.getResource().getIdElement().toUnqualifiedVersionless().getValue()).isEqualTo("Patient/123");
	}

	@Test
	public void testValidateFluent() throws Exception {

		OperationOutcome oo = new OperationOutcome();
		oo.addIssue().setDiagnostics("FOOBAR");
		final String msg = ourCtx.newXmlParser().encodeResourceToString(oo);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Patient p = new Patient();
		p.addName().addGiven("GIVEN");

		int idx = 0;
		MethodOutcome response;

		response = client.validate().resource(p).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$validate");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(capt.getAllValues().get(idx).getFirstHeader("content-type").getValue().replaceAll(";.*", "")).isEqualTo("application/xml+fhir");
		assertThat(extractBody(capt, idx)).isEqualTo("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"resource\"/><resource><Patient xmlns=\"http://hl7.org/fhir\"><name><given value=\"GIVEN\"/></name></Patient></resource></parameter></Parameters>");
		assertThat(response.getOperationOutcome()).isNotNull();
		assertThat(toOo(response.getOperationOutcome()).getIssueFirstRep().getDiagnosticsElement().getValue()).isEqualTo("FOOBAR");
		idx++;

		response = client.validate().resource(ourCtx.newXmlParser().encodeResourceToString(p)).encodedXml().execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$validate");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(capt.getAllValues().get(idx).getFirstHeader("content-type").getValue().replaceAll(";.*", "")).isEqualTo("application/xml+fhir");
		assertThat(extractBody(capt, idx)).isEqualTo("<Parameters xmlns=\"http://hl7.org/fhir\"><parameter><name value=\"resource\"/><resource><Patient xmlns=\"http://hl7.org/fhir\"><name><given value=\"GIVEN\"/></name></Patient></resource></parameter></Parameters>");
		assertThat(response.getOperationOutcome()).isNotNull();
		assertThat(toOo(response.getOperationOutcome()).getIssueFirstRep().getDiagnosticsElement().getValue()).isEqualTo("FOOBAR");
		idx++;

		response = client.validate().resource(ourCtx.newJsonParser().encodeResourceToString(p)).execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$validate");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(capt.getAllValues().get(idx).getFirstHeader("content-type").getValue().replaceAll(";.*", "")).isEqualTo("application/json+fhir");
		assertThat(extractBody(capt, idx)).isEqualTo("{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"resource\",\"resource\":{\"resourceType\":\"Patient\",\"name\":[{\"given\":[\"GIVEN\"]}]}}]}");
		assertThat(response.getOperationOutcome()).isNotNull();
		assertThat(toOo(response.getOperationOutcome()).getIssueFirstRep().getDiagnosticsElement().getValue()).isEqualTo("FOOBAR");
		idx++;

		response = client.validate().resource(ourCtx.newJsonParser().encodeResourceToString(p)).prettyPrint().execute();
		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$validate?_pretty=true");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(capt.getAllValues().get(idx).getFirstHeader("content-type").getValue().replaceAll(";.*", "")).isEqualTo("application/json+fhir");
		assertThat(extractBody(capt, idx)).contains("\"resourceType\": \"Parameters\",\n");
		assertThat(response.getOperationOutcome()).isNotNull();
		assertThat(toOo(response.getOperationOutcome()).getIssueFirstRep().getDiagnosticsElement().getValue()).isEqualTo("FOOBAR");
		idx++;
	}

	@Test
	public void testValidateNonFluent() throws Exception {

		OperationOutcome oo = new OperationOutcome();
		oo.addIssue().setDiagnostics("FOOBAR");
		final String msg = ourCtx.newXmlParser().encodeResourceToString(oo);

		ArgumentCaptor<HttpUriRequest> capt = ArgumentCaptor.forClass(HttpUriRequest.class);
		when(myHttpClient.execute(capt.capture())).thenReturn(myHttpResponse);
		when(myHttpResponse.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
		when(myHttpResponse.getEntity().getContentType()).thenReturn(new BasicHeader("content-type", Constants.CT_FHIR_XML + "; charset=UTF-8"));
		when(myHttpResponse.getEntity().getContent()).thenAnswer(new Answer<InputStream>() {
			@Override
			public InputStream answer(InvocationOnMock theInvocation) throws Throwable {
				return new ReaderInputStream(new StringReader(msg), Charset.forName("UTF-8"));
			}
		});

		IGenericClient client = ourCtx.newRestfulGenericClient("http://example.com/fhir");

		Patient p = new Patient();
		p.addName().addGiven("GIVEN");

		int idx = 0;
		MethodOutcome response;

		response = client.validate(p);

		assertThat(capt.getAllValues().get(idx).getURI().toASCIIString()).isEqualTo("http://example.com/fhir/Patient/$validate");
		assertThat(capt.getAllValues().get(idx).getRequestLine().getMethod()).isEqualTo("POST");
		assertThat(extractBody(capt, idx)).isEqualTo("{\"resourceType\":\"Parameters\",\"parameter\":[{\"name\":\"resource\",\"resource\":{\"resourceType\":\"Patient\",\"name\":[{\"given\":[\"GIVEN\"]}]}}]}");
		assertThat(response.getOperationOutcome()).isNotNull();
		assertThat(toOo(response.getOperationOutcome()).getIssueFirstRep().getDiagnosticsElement().getValue()).isEqualTo("FOOBAR");
		idx++;
	}

	private OperationOutcome toOo(IBaseOperationOutcome theOperationOutcome) {
		return (OperationOutcome) theOperationOutcome;
	}

	public final static class RestfulClientInstance implements IRestfulClient {
		@Override
		public IInterceptorService getInterceptorService() {
			return null;
		}

		@Override
		public void setInterceptorService(@Nonnull IInterceptorService theInterceptorService) {
			// nothing
		}

		@Override
		public <T extends IBaseResource> T fetchResourceFromUrl(Class<T> theResourceType, String theUrl) {
			return null;
		}

		@Override
		public FhirContext getFhirContext() {
			return null;
		}

		@Override
		public IHttpClient getHttpClient() {
			return null;
		}

		@Override
		public String getServerBase() {
			return null;
		}

		@Override
		public void registerInterceptor(Object theInterceptor) {
			// nothing
		}

		@Override
		public void setPrettyPrint(Boolean thePrettyPrint) {
			// nothing
		}

		@Override
		public void setSummary(SummaryEnum theSummary) {
			// nothing
		}

		@Override
		public void unregisterInterceptor(Object theInterceptor) {
			// nothing
		}

		@Override
		public void setFormatParamStyle(RequestFormatParamStyleEnum theRequestFormatParamStyle) {
			// nothing
		}

		@Override
		public EncodingEnum getEncoding() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setEncoding(EncodingEnum theEncoding) {
			// nothing
		}

	}

	@AfterAll
	public static void afterClassClearContext() {
		TestUtil.randomizeLocaleAndTimezone();
	}

	@BeforeAll
	public static void beforeClass() {
		ourCtx = FhirContext.forDstu2();
	}

}
