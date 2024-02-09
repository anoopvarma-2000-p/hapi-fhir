package org.hl7.fhir.common.hapi.validation.support;

import ca.uhn.fhir.context.ConfigurationException;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.fhirpath.BaseValidationTestWithInlineMocks;
import ca.uhn.fhir.i18n.Msg;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ValidationSupportChainTest extends BaseValidationTestWithInlineMocks {


	@Test
	public void testVersionCheck() {

		DefaultProfileValidationSupport ctx3 = new DefaultProfileValidationSupport(FhirContext.forDstu3());
		DefaultProfileValidationSupport ctx4 = new DefaultProfileValidationSupport(FhirContext.forR4());

		try {
			new ValidationSupportChain(ctx3, ctx4);
		} catch (ConfigurationException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(709) + "Trying to add validation support of version R4 to chain with 1 entries of version DSTU3");
		}

	}

	@Test
	public void testMissingContext() {
		IValidationSupport ctx = mock(IValidationSupport.class);
		try {
			new ValidationSupportChain(ctx);
		} catch (ConfigurationException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(708) + "Can not add validation support: getFhirContext() returns null");
		}
	}

	@Test
	public void fetchBinary_normally_returnsExpectedBinaries() {

		final byte[] EXPECTED_BINARY_CONTENT_1 = "dummyBinaryContent1".getBytes();
		final byte[] EXPECTED_BINARY_CONTENT_2 = "dummyBinaryContent2".getBytes();
		final String EXPECTED_BINARY_KEY_1 = "dummyBinaryKey1";
		final String EXPECTED_BINARY_KEY_2 = "dummyBinaryKey2";

		IValidationSupport validationSupport1 = createMockValidationSupportWithSingleBinary(EXPECTED_BINARY_KEY_1, EXPECTED_BINARY_CONTENT_1);
		IValidationSupport validationSupport2 = createMockValidationSupportWithSingleBinary(EXPECTED_BINARY_KEY_2, EXPECTED_BINARY_CONTENT_2);

		ValidationSupportChain validationSupportChain = new ValidationSupportChain(validationSupport1, validationSupport2);

		final byte[] actualBinaryContent1 = validationSupportChain.fetchBinary(EXPECTED_BINARY_KEY_1 );
		final byte[] actualBinaryContent2 = validationSupportChain.fetchBinary(EXPECTED_BINARY_KEY_2 );

		assertThat(actualBinaryContent1).containsExactly(EXPECTED_BINARY_CONTENT_1);
		assertThat(actualBinaryContent2).containsExactly(EXPECTED_BINARY_CONTENT_2);
		assertThat(validationSupportChain.fetchBinary("nonExistentKey")).isNull();
	}


	private static IValidationSupport createMockValidationSupport() {
		IValidationSupport validationSupport;
		validationSupport = mock(IValidationSupport.class);
		FhirContext mockContext = mock(FhirContext.class);
		when(mockContext.getVersion()).thenReturn(FhirVersionEnum.R4.getVersionImplementation());
		when(validationSupport.getFhirContext()).thenReturn(mockContext);
		return validationSupport;
	}


	private static IValidationSupport createMockValidationSupportWithSingleBinary(String expected_binary_key, byte[] expected_binary_content) {
		IValidationSupport validationSupport1  = createMockValidationSupport();
		when(validationSupport1.fetchBinary(expected_binary_key)).thenReturn(expected_binary_content);
		return validationSupport1;
	}
}
