package ca.uhn.fhir.jaxrs.server.util;

import ca.uhn.fhir.jaxrs.server.test.TestJaxRsDummyPatientProvider;
import ca.uhn.fhir.model.dstu2.resource.Parameters;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

public class JaxRsMethodBindingsTest {

	@BeforeEach
	public void setUp() {
		JaxRsMethodBindings.getClassBindings().clear();
	}

	@Test
	public void testFindMethodsForProviderNotDefinedMappingMethods() {
		assertThatExceptionOfType(NotImplementedOperationException.class).isThrownBy(() -> {
			new TestJaxRsDummyPatientProvider().getBindings().getBinding(RestOperationTypeEnum.UPDATE, "");
		});
	}

	@Test
	public void testFindMethodsForProviderWithMethods() {
		class TestFindPatientProvider extends TestJaxRsDummyPatientProvider {
			@Search
			public List<Patient> search(@RequiredParam(name = Patient.SP_NAME) final StringParam name) {
				return null;
			}
		}
		new TestFindPatientProvider();
		assertThat(new TestFindPatientProvider().getBindings().getBinding(RestOperationTypeEnum.SEARCH_TYPE, "").getMethod().getDeclaringClass()).isEqualTo(TestFindPatientProvider.class);
	}

	@Test
	public void testFindMethodsFor2ProvidersWithMethods() {
		class TestFindPatientProvider extends TestJaxRsDummyPatientProvider {
			@Search
			public List<Patient> search(@RequiredParam(name = Patient.SP_NAME) final StringParam name) {
				return null;
			}
		}
		class TestUpdatePatientProvider extends TestJaxRsDummyPatientProvider {
			@Update
			public MethodOutcome update(@IdParam final IdDt theId, @ResourceParam final Patient patient) {
				return null;
			}
		}
		assertThat(new TestFindPatientProvider().getBindings().getBinding(RestOperationTypeEnum.SEARCH_TYPE, "").getMethod().getDeclaringClass()).isEqualTo(TestFindPatientProvider.class);
		assertThat(new TestUpdatePatientProvider().getBindings().getBinding(RestOperationTypeEnum.UPDATE, "").getMethod().getDeclaringClass()).isEqualTo(TestFindPatientProvider.class);
	}

	@Test
	public void testFindMethodsWithDoubleMethodsDeclaration() {
		class TestDoubleSearchProvider extends TestJaxRsDummyPatientProvider {
			@Search
			public List<Patient> search1(@RequiredParam(name = Patient.SP_NAME) final StringParam name) {
				return null;
			}

			@Search
			public List<Patient> search2(@RequiredParam(name = Patient.SP_NAME) final StringParam name) {
				return null;
			}
		}
		try {
			new TestDoubleSearchProvider();
			fail("");
		} catch (IllegalArgumentException e) {
			assertThat(e.getMessage()).contains("search1");
			assertThat(e.getMessage()).contains("search2");
		}
	}

	@Test
	public void testFindMethodsWithMultipleMethods() {
		class TestFindPatientProvider extends TestJaxRsDummyPatientProvider {
			@Search
			public List<Patient> search(@RequiredParam(name = Patient.SP_NAME) final StringParam name) {
				return null;
			}

			@Update
			public MethodOutcome update(@IdParam final IdDt theId, @ResourceParam final Patient patient) {
				return null;
			}

			@Operation(name = "firstMethod", idempotent = true, returnParameters = {@OperationParam(name = "return", type = StringDt.class)})
			public Parameters firstMethod(@OperationParam(name = "dummy") StringDt dummyInput) {
				return null;
			}

			@Operation(name = "secondMethod", returnParameters = {@OperationParam(name = "return", type = StringDt.class)})
			public Parameters secondMethod(@OperationParam(name = "dummy") StringDt dummyInput) {
				return null;
			}
		}
		JaxRsMethodBindings bindings = new TestFindPatientProvider().getBindings();
		assertThat(bindings.getBinding(RestOperationTypeEnum.SEARCH_TYPE, "").getMethod().getName()).isEqualTo("search");
		assertThat(bindings.getBinding(RestOperationTypeEnum.UPDATE, "").getMethod().getName()).isEqualTo("update");
		assertThat(bindings.getBinding(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "$firstMethod").getMethod().getName()).isEqualTo("firstMethod");
		assertThat(bindings.getBinding(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "$secondMethod").getMethod().getName()).isEqualTo("secondMethod");
		try {
			bindings.getBinding(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE, "$thirdMethod");
			fail("");
		} catch (NotImplementedOperationException e) {
		}
	}

}
