package ca.uhn.hapi.fhir.cdshooks.svc;

import ca.uhn.hapi.fhir.cdshooks.api.ICdsMethod;
import ca.uhn.hapi.fhir.cdshooks.api.json.CdsServiceJson;
import ca.uhn.hapi.fhir.cdshooks.api.json.CdsServiceRequestJson;
import ca.uhn.hapi.fhir.cdshooks.api.json.CdsServiceResponseCardJson;
import ca.uhn.hapi.fhir.cdshooks.api.json.CdsServiceResponseJson;
import ca.uhn.test.util.LogbackCaptureTestExtension;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.annotation.Nonnull;
import java.util.function.Function;

import static ca.uhn.test.util.LogbackCaptureTestExtension.eventWithLevelAndMessageContains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

@ExtendWith(MockitoExtension.class)
class CdsServiceCacheTest {
	private static final String TEST_KEY = "testKey";
	private static final String MODULE_ID = "moduleId";
	@RegisterExtension
	final LogbackCaptureTestExtension myLogCapture = new LogbackCaptureTestExtension((Logger) CdsServiceCache.ourLog, Level.ERROR);
	@InjectMocks
	private CdsServiceCache myFixture;

	@Test
	void registerDynamicServiceShouldRegisterServiceWhenServiceNotRegistered() {
		// setup
		final Function<CdsServiceRequestJson, CdsServiceResponseJson> serviceFunction = withFunction();
		final CdsServiceJson cdsServiceJson = withCdsServiceJson();
		// execute
		myFixture.registerDynamicService(TEST_KEY, serviceFunction, cdsServiceJson, true, MODULE_ID);
		// validate
		assertThat(myFixture.myServiceMap).hasSize(1);
		final CdsDynamicPrefetchableServiceMethod cdsMethod = (CdsDynamicPrefetchableServiceMethod) myFixture.myServiceMap.get(TEST_KEY);
		assertThat(cdsMethod.getFunction()).isEqualTo(serviceFunction);
		assertThat(cdsMethod.getCdsServiceJson()).isEqualTo(cdsServiceJson);
		assertThat(cdsMethod.isAllowAutoFhirClientPrefetch()).isTrue();
		assertThat(myFixture.myCdsServiceJson.getServices()).hasSize(1);
		assertThat(myFixture.myCdsServiceJson.getServices().get(0)).isEqualTo(cdsServiceJson);
	}

	@Test
	void registerDynamicServiceShouldNotRegisterServiceWhenServiceAlreadyRegistered() {
		// setup
		final Function<CdsServiceRequestJson, CdsServiceResponseJson> serviceFunction = withFunction();
		final CdsServiceJson cdsServiceJson = withCdsServiceJson();
		final Function<CdsServiceRequestJson, CdsServiceResponseJson> serviceFunction2 = withFunction();
		final CdsServiceJson cdsServiceJson2 = withCdsServiceJson();
		final String expectedLogMessage = "CDS service with serviceId: testKey for moduleId: moduleId, already exists. It will not be overwritten!";
		// execute
		myFixture.registerDynamicService(TEST_KEY, serviceFunction, cdsServiceJson, true, MODULE_ID);
		myFixture.registerDynamicService(TEST_KEY, serviceFunction2, cdsServiceJson2, false, MODULE_ID);
		// validate
		assertThat(myFixture.myServiceMap).hasSize(1);
		final CdsDynamicPrefetchableServiceMethod cdsMethod = (CdsDynamicPrefetchableServiceMethod) myFixture.myServiceMap.get(TEST_KEY);
		assertThat(cdsMethod.getFunction()).isEqualTo(serviceFunction);
		assertThat(cdsMethod.getCdsServiceJson()).isEqualTo(cdsServiceJson);
		assertThat(cdsMethod.isAllowAutoFhirClientPrefetch()).isTrue();
		assertThat(myFixture.myCdsServiceJson.getServices()).hasSize(1);
		assertThat(myFixture.myCdsServiceJson.getServices().get(0)).isEqualTo(cdsServiceJson);
		assertThat(myLogCapture.getLogEvents(), contains(eventWithLevelAndMessageContains(Level.ERROR, expectedLogMessage)));
	}

	@Test
	void unregisterServiceMethodShouldReturnsServiceWhenServiceRegistered() {
		// setup
		final Function<CdsServiceRequestJson, CdsServiceResponseJson> serviceFunction = withFunction();
		final CdsServiceJson cdsServiceJson = withCdsServiceJson();
		myFixture.registerDynamicService(TEST_KEY, serviceFunction, cdsServiceJson, true, MODULE_ID);
		// execute
		final CdsDynamicPrefetchableServiceMethod cdsMethod = (CdsDynamicPrefetchableServiceMethod) myFixture.unregisterServiceMethod(TEST_KEY, MODULE_ID);
		// validate
		assertThat(myFixture.myServiceMap.isEmpty()).isTrue();
		assertThat(cdsMethod.getFunction()).isEqualTo(serviceFunction);
		assertThat(cdsMethod.getCdsServiceJson()).isEqualTo(cdsServiceJson);
		assertThat(cdsMethod.isAllowAutoFhirClientPrefetch()).isTrue();
		assertThat(myFixture.myCdsServiceJson.getServices()).isEmpty();
	}

	@Test
	void unregisterServiceMethodShouldReturnNullWhenServiceNotRegistered() {
		// setup
		final String expectedLogMessage = "CDS service with serviceId: testKey for moduleId: moduleId, is not registered. Nothing to remove!";
		// execute
		final ICdsMethod actual = myFixture.unregisterServiceMethod(TEST_KEY, MODULE_ID);
		// validate
		assertThat(actual).isNull();
		assertThat(myLogCapture.getLogEvents(), contains(eventWithLevelAndMessageContains(Level.ERROR, expectedLogMessage)));
	}

	@Nonnull
	private static CdsServiceJson withCdsServiceJson() {
		final CdsServiceJson cdsServiceJson = new CdsServiceJson();
		cdsServiceJson.setId(TEST_KEY);
		return cdsServiceJson;
	}

	@Nonnull
	private static Function<CdsServiceRequestJson, CdsServiceResponseJson> withFunction() {
		return (CdsServiceRequestJson theCdsServiceRequestJson) -> {
			final CdsServiceResponseJson cdsServiceResponseJson = new CdsServiceResponseJson();
			final CdsServiceResponseCardJson card = new CdsServiceResponseCardJson();
			cdsServiceResponseJson.addCard(card);
			return cdsServiceResponseJson;
		};
	}
}
