package ca.uhn.fhir.okhttp;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.okhttp.client.OkHttpRestfulClientFactory;
import ca.uhn.fhir.test.BaseFhirVersionParameterizedTest;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLHandshakeException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.fail;


public class OkHttpRestfulClientFactoryTest extends BaseFhirVersionParameterizedTest {

	private OkHttpRestfulClientFactory clientFactory;

	@BeforeEach
	public void setUp() {
		clientFactory = new OkHttpRestfulClientFactory();
	}

	@Test
	public void testGetNativeClient_noClientSet_returnsADefault() throws Exception {
		Call.Factory actualNativeClient = clientFactory.getNativeClient();

		assertThat(actualNativeClient).isNotNull();
	}

	@Test
	public void testGetNativeClient_noProxySet_defaultHasNoProxySet() throws Exception {
		OkHttpClient actualNativeClient = (OkHttpClient) clientFactory.getNativeClient();

		assertThat(actualNativeClient.proxy()).isEqualTo(null);
	}

	@Test
	public void testSetHttpClient() {
		OkHttpClient okHttpClient = new OkHttpClient.Builder().writeTimeout(5000, TimeUnit.MILLISECONDS).build();

		clientFactory.setHttpClient(okHttpClient);

		assertThat(clientFactory.getNativeClient()).isSameAs(okHttpClient);
	}

	@Test
	public void testSocketTimeout() {
		clientFactory.setSocketTimeout(1515);

		assertThat(((OkHttpClient) clientFactory.getNativeClient()).readTimeoutMillis()).isEqualTo(1515);
		assertThat(((OkHttpClient) clientFactory.getNativeClient()).writeTimeoutMillis()).isEqualTo(1515);
	}

	@Test
	public void testConnectTimeout() {
		clientFactory.setConnectTimeout(1516);

		assertThat(((OkHttpClient) clientFactory.getNativeClient()).connectTimeoutMillis()).isEqualTo(1516);
	}

	@ParameterizedTest
	@MethodSource("baseParamsProvider")
	public void testNativeClientHttp(FhirVersionEnum theFhirVersion) throws Exception {
		FhirVersionParams fhirVersionParams = getFhirVersionParams(theFhirVersion);
		OkHttpRestfulClientFactory clientFactory = new OkHttpRestfulClientFactory(fhirVersionParams.getFhirContext());
		OkHttpClient client = (OkHttpClient) clientFactory.getNativeClient();

		Request request = new Request.Builder()
			.url(fhirVersionParams.getPatientEndpoint())
			.build();

		Response response = client.newCall(request).execute();
		assertThat(response.code()).isEqualTo(200);
		String json = response.body().string();
		IBaseResource bundle = fhirVersionParams.getFhirContext().newJsonParser().parseResource(json);
		assertThat(bundle.getStructureFhirVersionEnum()).isEqualTo(fhirVersionParams.getFhirVersion());
	}

	@ParameterizedTest
	@MethodSource("baseParamsProvider")
	public void testNativeClientHttpsNoCredentials(FhirVersionEnum theFhirVersion) {
		FhirVersionParams fhirVersionParams = getFhirVersionParams(theFhirVersion);
		OkHttpRestfulClientFactory clientFactory = new OkHttpRestfulClientFactory(fhirVersionParams.getFhirContext());
		OkHttpClient unauthenticatedClient = (OkHttpClient) clientFactory.getNativeClient();

		try {
			Request request = new Request.Builder()
				.url(fhirVersionParams.getSecuredPatientEndpoint())
				.build();
			unauthenticatedClient.newCall(request).execute();
			fail("");
		} catch (Exception e) {
			assertThat(e.getClass()).isEqualTo(SSLHandshakeException.class);
		}
	}

	@ParameterizedTest
	@MethodSource("baseParamsProvider")
	public void testGenericClientHttp(FhirVersionEnum theFhirVersion) {
		FhirVersionParams fhirVersionParams = getFhirVersionParams(theFhirVersion);
		String base = fhirVersionParams.getBase();
		FhirContext context = fhirVersionParams.getFhirContext();
		context.setRestfulClientFactory(new OkHttpRestfulClientFactory(context));
		IBaseResource bundle = context.newRestfulGenericClient(base).search().forResource("Patient").execute();
		assertThat(bundle.getStructureFhirVersionEnum()).isEqualTo(theFhirVersion);
	}

	@ParameterizedTest
	@MethodSource("baseParamsProvider")
	public void testGenericClientHttpsNoCredentials(FhirVersionEnum theFhirVersion) {
		FhirVersionParams fhirVersionParams = getFhirVersionParams(theFhirVersion);
		String secureBase = fhirVersionParams.getSecureBase();
		FhirContext context = fhirVersionParams.getFhirContext();
		context.setRestfulClientFactory(new OkHttpRestfulClientFactory(context));
		try {
			context.newRestfulGenericClient(secureBase).search().forResource("Patient").execute();
			fail("");
		} catch (Exception e) {
			assertThat(e.getMessage()).contains("HAPI-1357: Failed to retrieve the server metadata statement during client initialization");
			assertThat(e.getCause().getCause().getClass()).isEqualTo(SSLHandshakeException.class);
		}
	}
}
