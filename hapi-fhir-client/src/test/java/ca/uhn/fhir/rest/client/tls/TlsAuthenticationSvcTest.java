package ca.uhn.fhir.rest.client.tls;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.tls.KeyStoreInfo;
import ca.uhn.fhir.tls.TlsAuthentication;
import ca.uhn.fhir.tls.TrustStoreInfo;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


public class TlsAuthenticationSvcTest {

	private KeyStoreInfo myServerKeyStoreInfo;
	private TrustStoreInfo myServerTrustStoreInfo;
	private TlsAuthentication myServerTlsAuthentication;

	private KeyStoreInfo myClientKeyStoreInfo;
	private TrustStoreInfo myClientTrustStoreInfo;
	private TlsAuthentication myClientTlsAuthentication;

	@BeforeEach
	public void beforeEach(){
		myServerKeyStoreInfo = new KeyStoreInfo("classpath:/server-keystore.p12", "changeit", "changeit", "server");
		myServerTrustStoreInfo = new TrustStoreInfo("classpath:/server-truststore.p12", "changeit", "client");
		myServerTlsAuthentication = new TlsAuthentication(Optional.of(myServerKeyStoreInfo), Optional.of(myServerTrustStoreInfo));

		myClientKeyStoreInfo = new KeyStoreInfo("classpath:/client-keystore.p12", "changeit", "changeit", "client");
		myClientTrustStoreInfo = new TrustStoreInfo("classpath:/client-truststore.p12", "changeit", "server");
		myClientTlsAuthentication = new TlsAuthentication(Optional.of(myClientKeyStoreInfo), Optional.of(myClientTrustStoreInfo));
	}

	@Test
	public void testCreateSslContextEmpty(){
		TlsAuthentication emptyAuthentication = null;
		try {
			TlsAuthenticationSvc.createSslContext(emptyAuthentication);
			fail("");		} catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo("theTlsAuthentication cannot be null");
		}
	}

	@Test
	public void testCreateSslContextPresent(){
		SSLContext result = TlsAuthenticationSvc.createSslContext(myServerTlsAuthentication);
		assertThat(result.getProtocol()).isEqualTo("TLS");
	}

	@Test
	public void testCreateSslContextPresentInvalid(){
		KeyStoreInfo invalidKeyStoreInfo = new KeyStoreInfo("file:///INVALID.p12", "changeit", "changeit", "server");
		TlsAuthentication invalidTlsAuthentication = new TlsAuthentication(Optional.of(invalidKeyStoreInfo), Optional.of(myServerTrustStoreInfo));
		try {
			TlsAuthenticationSvc.createSslContext(invalidTlsAuthentication);
			fail("");		} catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(2102) + "Failed to create SSLContext");
		}
	}

	@Test
	public void testCreateKeyStoreP12() throws Exception {
		KeyStore keyStore = TlsAuthenticationSvc.createKeyStore(myServerKeyStoreInfo);
		assertThat(keyStore.getKey(myServerKeyStoreInfo.getAlias(), myServerKeyStoreInfo.getKeyPass())).isNotNull();
	}

	@Test
	public void testCreateKeyStoreJKS() throws Exception {
		KeyStoreInfo keyStoreInfo = new KeyStoreInfo("classpath:/keystore.jks", "changeit", "changeit", "client");
		KeyStore keyStore = TlsAuthenticationSvc.createKeyStore(keyStoreInfo);
		assertThat(keyStore.getKey(keyStoreInfo.getAlias(), keyStoreInfo.getKeyPass())).isNotNull();
	}

	@Test
	public void testCreateKeyStoreNonExistentFile() {
		KeyStoreInfo keyStoreInfo = new KeyStoreInfo("classpath:/non-existent.p12", "changeit", "changeit", "server");
		try {
			TlsAuthenticationSvc.createKeyStore(keyStoreInfo);
			fail("");		}
		catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(2103) + "Failed to create KeyStore");
		}
	}

	@Test
	public void testCreateTrustStoreJks() throws Exception {
		TrustStoreInfo trustStoreInfo = new TrustStoreInfo("classpath:/truststore.jks", "changeit",  "client");
		KeyStore keyStore = TlsAuthenticationSvc.createKeyStore(trustStoreInfo);
		assertThat(keyStore.getCertificate(trustStoreInfo.getAlias())).isNotNull();
	}

	@Test
	public void testCreateTrustStoreP12() throws Exception {
		KeyStore keyStore = TlsAuthenticationSvc.createKeyStore(myServerTrustStoreInfo);
		assertThat(keyStore.getCertificate(myServerTrustStoreInfo.getAlias())).isNotNull();
	}

	@Test
	public void testCreateTrustStoreNonExistentFile() throws Exception {
		TrustStoreInfo trustStoreInfo = new TrustStoreInfo("classpath:/non-existent.p12", "changeit", "server");
		try {
			TlsAuthenticationSvc.createKeyStore(trustStoreInfo);
			fail("");		} catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(2103) + "Failed to create KeyStore");
		}
	}

	@Test
	public void testCreateTrustManager() throws Exception {
		X509TrustManager trustManager = TlsAuthenticationSvc.createTrustManager(Optional.of(myClientTrustStoreInfo));
		KeyStore keyStore = TlsAuthenticationSvc.createKeyStore(myServerKeyStoreInfo);
		Certificate serverCertificate = keyStore.getCertificate(myServerKeyStoreInfo.getAlias());

		assertThat(trustManager.getAcceptedIssuers().length).isEqualTo(1);
		assertThat(trustManager.getAcceptedIssuers()[0]).isEqualTo(serverCertificate);
	}

	@Test
	public void testCreateTrustManagerNoTrustStore() {
		// trust manager should contain common certifications if no trust store information is used
		X509TrustManager trustManager = TlsAuthenticationSvc.createTrustManager(Optional.empty());
		assertThat(trustManager.getAcceptedIssuers().length).isNotEqualTo(0);
	}

	@Test
	public void testCreateTrustManagerInvalid() {
		TrustStoreInfo invalidKeyStoreInfo = new TrustStoreInfo("file:///INVALID.p12", "changeit", "client");
		try {
			TlsAuthenticationSvc.createTrustManager(Optional.of(invalidKeyStoreInfo));
			fail("");		} catch (Exception e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(2105) + "Failed to create X509TrustManager");
		}
	}

	@Test
	public void testCreateHostnameVerifierEmptyTrustStoreInfo(){
		Optional<TrustStoreInfo> trustStoreInfo = Optional.empty();
		HostnameVerifier result = TlsAuthenticationSvc.createHostnameVerifier(trustStoreInfo);
		assertThat(result.getClass()).isEqualTo(NoopHostnameVerifier.class);
	}

	@Test
	public void testCreateHostnameVerifierPresentTrustStoreInfo(){
		Optional<TrustStoreInfo> trustStoreInfo = Optional.of(myServerTrustStoreInfo);
		HostnameVerifier result = TlsAuthenticationSvc.createHostnameVerifier(trustStoreInfo);
		assertThat(result.getClass()).isEqualTo(DefaultHostnameVerifier.class);
	}
}
