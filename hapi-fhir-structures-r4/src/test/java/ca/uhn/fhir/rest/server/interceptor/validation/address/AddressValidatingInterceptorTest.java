package ca.uhn.fhir.rest.server.interceptor.validation.address;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.validation.address.impl.LoquateAddressValidator;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Person;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.Properties;

import static ca.uhn.fhir.rest.server.interceptor.validation.address.AddressValidatingInterceptor.ADDRESS_VALIDATION_DISABLED_HEADER;
import static ca.uhn.fhir.rest.server.interceptor.validation.address.AddressValidatingInterceptor.PROPERTY_EXTENSION_URL;
import static ca.uhn.fhir.rest.server.interceptor.validation.address.AddressValidatingInterceptor.PROPERTY_VALIDATOR_CLASS;
import static ca.uhn.fhir.rest.server.interceptor.validation.address.IAddressValidator.ADDRESS_VALIDATION_EXTENSION_URL;
import static ca.uhn.fhir.rest.server.interceptor.validation.address.impl.BaseRestfulValidator.PROPERTY_SERVICE_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressValidatingInterceptorTest {

	private static FhirContext ourCtx = FhirContext.forR4();

	private AddressValidatingInterceptor myInterceptor;

	private IAddressValidator myValidator;

	private RequestDetails myRequestDetails;

	@Test
	@Disabled
	public void testValidationCallAgainstLiveLoquateEndpoint() {
		Properties config = new Properties();
		config.setProperty(PROPERTY_VALIDATOR_CLASS, LoquateAddressValidator.class.getCanonicalName());
		config.setProperty(PROPERTY_SERVICE_KEY, "KR26-JA29-HB16-PA11"); // Replace with a real key when testing
		AddressValidatingInterceptor interceptor = new AddressValidatingInterceptor(config);

		Address address = new Address();
		address.setUse(Address.AddressUse.WORK);
		address.addLine("100 Somewhere");
		address.setCity("Burloak");
		address.setPostalCode("A0A0A0");
		address.setCountry("Canada");
		interceptor.validateAddress(address, ourCtx);

		assertThat(address.hasExtension()).isTrue();
		assertThat(address.getExtensionFirstRep().getValueAsPrimitive().getValueAsString()).isEqualTo("true");
		assertThat(address.getExtensionByUrl(IAddressValidator.ADDRESS_QUALITY_EXTENSION_URL).getValueAsPrimitive().getValueAsString()).isEqualTo("E");

		assertThat(address.getText()).isEqualTo("100 Somewhere, Burloak");
		assertThat(address.getLine()).hasSize(1);
		assertThat(address.getLine().get(0).getValueAsString()).isEqualTo("100 Somewhere");
		assertThat(address.getCity()).isEqualTo("Burloak");
		assertThat(address.getPostalCode()).isEqualTo("A0A0A0");
		assertThat(address.getCountry()).isEqualTo("Canada");
	}

	@Test
	void start() throws Exception {
		AddressValidatingInterceptor interceptor = new AddressValidatingInterceptor(new Properties());
		assertThat(interceptor.getAddressValidator()).isNull();

		Properties props = new Properties();
		props.setProperty(PROPERTY_VALIDATOR_CLASS, "RandomService");
		try {
			new AddressValidatingInterceptor(props);
			fail("");		} catch (Exception e) {
			// expected
		}

		props.setProperty(PROPERTY_VALIDATOR_CLASS, TestAddressValidator.class.getName());
		interceptor = new AddressValidatingInterceptor(props);
		assertThat(interceptor.getAddressValidator()).isNotNull();
	}

	@Test
	public void testEmptyRequest() {
		try {
			myInterceptor.handleRequest(null, null);
		} catch (Exception ex) {
			fail("");		}

		try {
			myInterceptor.setAddressValidator(null);
			myInterceptor.handleRequest(null, null);
		} catch (Exception ex) {
			fail("");		}
	}

	@BeforeEach
	void setup() {
		myValidator = mock(IAddressValidator.class);
		when(myValidator.isValid(any(), any())).thenReturn(mock(AddressValidationResult.class));

		myRequestDetails = mock(RequestDetails.class);
		when(myRequestDetails.getFhirContext()).thenReturn(ourCtx);

		Properties properties = getProperties();
		myInterceptor = new AddressValidatingInterceptor(properties);
		myInterceptor.setAddressValidator(myValidator);
	}

	@Nonnull
	private Properties getProperties() {
		Properties properties = new Properties();
		properties.setProperty(PROPERTY_VALIDATOR_CLASS, TestAddressValidator.class.getName());
		return properties;
	}

	@Test
	public void testDisablingValidationViaHeader() {
		when(myRequestDetails.getHeaders(eq(ADDRESS_VALIDATION_DISABLED_HEADER))).thenReturn(Arrays.asList(new String[]{"True"}));

		Person p = new Person();
		AddressValidatingInterceptor spy = Mockito.spy(myInterceptor);
		spy.resourcePreCreate(myRequestDetails, p);

		Mockito.verify(spy, times(0)).validateAddress(any(), any());
	}

	@Test
	public void testValidationServiceError() {
		myValidator = mock(IAddressValidator.class);
		when(myValidator.isValid(any(), any())).thenThrow(new RuntimeException());
		myInterceptor.setAddressValidator(myValidator);

		Address address = new Address();
		myInterceptor.validateAddress(address, ourCtx);
		Extension ext = assertValidationErrorExtension(address);
		assertThat(ext.hasExtension()).isTrue();
		assertThat(ext.getExtensionFirstRep().getUrl()).isEqualTo("error");
	}

	@Test
	public void testValidationWithCustomUrl() {
		myInterceptor.getProperties().setProperty(PROPERTY_EXTENSION_URL, "MY_URL");
		Address address = new Address();
		address.setCity("City");
		address.addLine("Line");
		AddressValidationResult res = new AddressValidationResult();
		res.setValidatedAddressString("City, Line");
		res.setValidatedAddress(address);
		when(myValidator.isValid(any(), any())).thenReturn(res);

		Address addressToValidate = new Address();
		myInterceptor.validateAddress(addressToValidate, ourCtx);

		assertThat(res.toString()).isNotNull();
		assertThat(addressToValidate.hasExtension()).isTrue();
		assertThat(addressToValidate.getExtensionByUrl("MY_URL")).isNotNull();
		assertThat(address.hasExtension()).isFalse();
		assertThat(addressToValidate.getCity()).isEqualTo(address.getCity());
		assertThat(address.getLine().get(0).equalsDeep(addressToValidate.getLine().get(0))).isTrue();
	}

	@Test
	void validate() {
		Address address = new Address();
		address.addLine("Line");
		address.setCity("City");

		myInterceptor.validateAddress(address, ourCtx);
		assertValidationErrorValue(address, "true");
	}

	private Extension assertValidationErrorExtension(Address theAddress) {
		assertThat(theAddress.hasExtension()).isTrue();
		assertThat(theAddress.getExtension()).hasSize(1);
		assertThat(theAddress.getExtensionFirstRep().getUrl()).isEqualTo(IAddressValidator.ADDRESS_VALIDATION_EXTENSION_URL);
		return theAddress.getExtensionFirstRep();
	}

	private void assertValidationErrorValue(Address theAddress, String theValidationResult) {
		Extension ext = assertValidationErrorExtension(theAddress);
		assertThat(ext.getValueAsPrimitive().getValueAsString()).isEqualTo(theValidationResult);
	}

	@Test
	void validateOnCreate() {
		Address address = new Address();
		address.addLine("Line");
		address.setCity("City");

		Person person = new Person();
		person.addAddress(address);

		myInterceptor.resourcePreCreate(myRequestDetails, person);

		assertValidationErrorValue(person.getAddressFirstRep(), "true");
	}

	@Test
	void validateOnUpdate() {
		Address validAddress = new Address();
		validAddress.addLine("Line");
		validAddress.setCity("City");
		validAddress.addExtension(IAddressValidator.ADDRESS_VALIDATION_EXTENSION_URL, new StringType("false"));

		Address notValidatedAddress = new Address();
		notValidatedAddress.addLine("Line 2");
		notValidatedAddress.setCity("City 2");

		Person person = new Person();
		person.addAddress(validAddress);
		person.addAddress(notValidatedAddress);

		myInterceptor.resourcePreUpdate(myRequestDetails, null, person);

		verify(myValidator, times(1)).isValid(any(), any());
		assertValidationErrorValue(person.getAddress().get(0), "false");
		assertValidationErrorValue(person.getAddress().get(1), "true");
	}

	@Test
	void validateOnValidInvalid() {
		Address address = new Address();
		address.addLine("Line");
		address.setCity("City");

		Person person = new Person();
		person.addAddress(address);

		AddressValidationResult validationResult = new AddressValidationResult();
		validationResult.setValid(true);
		when(myValidator.isValid(eq(address), any())).thenReturn(validationResult);
		myInterceptor.resourcePreUpdate(myRequestDetails, null, person);

		assertValidationErrorValue(person.getAddress().get(0), "false");

		when(myValidator.isValid(eq(address), any())).thenThrow(new RuntimeException());

		myInterceptor.resourcePreUpdate(myRequestDetails, null, person);

		Extension ext = assertValidationErrorExtension(address);
		assertThat(ext).isNotNull();
		assertThat(ext.getValue()).isNull();
		assertThat(ext.hasExtension()).isTrue();

	}

	public static class TestAddressValidator implements IAddressValidator {
		@Override
		public AddressValidationResult isValid(IBase theAddress, FhirContext theFhirContext) throws AddressValidationException {
			return null;
		}
	}
}
