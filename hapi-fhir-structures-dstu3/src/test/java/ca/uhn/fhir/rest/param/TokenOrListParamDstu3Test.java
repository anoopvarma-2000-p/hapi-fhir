package ca.uhn.fhir.rest.param;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.QualifiedParamList;
import ca.uhn.fhir.util.TestUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenOrListParamDstu3Test {

	private static FhirContext ourCtx = FhirContext.forDstu3();

	/**
	 * See #192
	 */
	@Test
	public void testParseExcaped() {
		TokenOrListParam params = new TokenOrListParam();
		params.setValuesAsQueryTokens(ourCtx, null, QualifiedParamList.singleton("system|code-include-but-not-end-with-comma\\,suffix"));

		assertThat(params.getListAsCodings()).hasSize(1);
		assertThat(params.getListAsCodings().get(0).getSystemElement().getValue()).isEqualTo("system");
		assertThat(params.getListAsCodings().get(0).getCodeElement().getValue()).isEqualTo("code-include-but-not-end-with-comma,suffix");
	}

	@AfterAll
	public static void afterClassClearContext() {
		TestUtil.randomizeLocaleAndTimezone();
	}

}
