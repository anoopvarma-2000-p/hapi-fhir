package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.rest.annotation.ConditionalUrlParam;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.method.BaseMethodBinding;
import ca.uhn.fhir.rest.server.method.SearchMethodBinding;
import ca.uhn.fhir.rest.server.method.SearchParameter;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import ca.uhn.fhir.system.HapiSystemProperties;
import org.hl7.fhir.dstu2.hapi.rest.server.ServerConformanceProvider;
import org.hl7.fhir.dstu2.model.Conformance;
import org.hl7.fhir.dstu2.model.Conformance.ConditionalDeleteStatus;
import org.hl7.fhir.dstu2.model.Conformance.ConformanceRestComponent;
import org.hl7.fhir.dstu2.model.Conformance.ConformanceRestResourceComponent;
import org.hl7.fhir.dstu2.model.Conformance.SystemRestfulInteraction;
import org.hl7.fhir.dstu2.model.Conformance.TypeRestfulInteraction;
import org.hl7.fhir.dstu2.model.DateType;
import org.hl7.fhir.dstu2.model.DiagnosticReport;
import org.hl7.fhir.dstu2.model.IdType;
import org.hl7.fhir.dstu2.model.OperationDefinition;
import org.hl7.fhir.dstu2.model.Patient;
import org.hl7.fhir.dstu2.model.StringType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerConformanceProviderHl7OrgDstu2Test {

  static {
    HapiSystemProperties.enableTestMode();
  }

  private static FhirContext ourCtx = FhirContext.forDstu2Hl7Org();
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ServerConformanceProviderHl7OrgDstu2Test.class);

	private HttpServletRequest createHttpServletRequest() {
		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getRequestURI()).thenReturn("/FhirStorm/fhir/Patient/_search");
		when(req.getServletPath()).thenReturn("/fhir");
		when(req.getRequestURL()).thenReturn(new StringBuffer().append("http://fhirstorm.dyndns.org:8080/FhirStorm/fhir/Patient/_search"));
		when(req.getContextPath()).thenReturn("/FhirStorm");
		return req;
	}

	private ServletConfig createServletConfig() {
		ServletConfig sc = mock(ServletConfig.class);
		when(sc.getServletContext()).thenReturn(null);
		return sc;
	}

	@Test
	public void testConditionalOperations() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ConditionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestResourceComponent res = conformance.getRest().get(0).getResource().get(1);
		assertThat(res.getType()).isEqualTo("Patient");

		assertThat(res.getConditionalCreate()).isTrue();
		assertThat(res.getConditionalDelete()).isEqualTo(ConditionalDeleteStatus.SINGLE);
		assertThat(res.getConditionalUpdate()).isTrue();
	}
	
	@Test
	public void testExtendedOperationReturningBundle() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ProviderWithExtendedOperationReturningBundle());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertThat(conformance.getRest().get(0).getOperation()).hasSize(1);
		assertThat(conformance.getRest().get(0).getOperation().get(0).getName()).isEqualTo("$everything");
		assertThat(conformance.getRest().get(0).getOperation().get(0).getDefinition().getReference()).isEqualTo("OperationDefinition/Patient-i-everything");
	}

	
	@Test
	public void testExtendedOperationReturningBundleOperation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ProviderWithExtendedOperationReturningBundle());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		OperationDefinition opDef = sc.readOperationDefinition(new IdType("OperationDefinition/Patient-i-everything"), createRequestDetails(rs));
				
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(opDef);
		ourLog.info(conf);

		assertThat(opDef.getCode()).isEqualTo("$everything");
		assertThat(opDef.getIdempotent()).isEqualTo(true);
	}

	@Test
	public void testInstanceHistorySupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new InstanceHistoryProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf).contains("<interaction><code value=\"" + TypeRestfulInteraction.HISTORYINSTANCE.toCode() + "\"/></interaction>");
	}

	@Test
	public void testMultiOptionalDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new MultiOptionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				SearchParameter param = (SearchParameter) binding.getParameters().iterator().next();
				assertThat(param.getDescription()).isEqualTo("The patient's identifier");
				found = true;
			}
		}

		assertThat(found).isTrue();
		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertThat(conf).contains("<documentation value=\"The patient's identifier\"/>");
		assertThat(conf).contains("<documentation value=\"The patient's name\"/>");
		assertThat(conf).contains("<type value=\"token\"/>");
	}

	@Test
	public void testNonConditionalOperations() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new NonConditionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestResourceComponent res = conformance.getRest().get(0).getResource().get(1);
		assertThat(res.getType()).isEqualTo("Patient");

		assertThat(res.getConditionalCreateElement().getValue()).isNull();
		assertThat(res.getConditionalDeleteElement().getValue()).isNull();
		assertThat(res.getConditionalUpdateElement().getValue()).isNull();
	}

	@Test
	public void testOperationDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SearchProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				SearchParameter param = (SearchParameter) binding.getParameters().iterator().next();
				assertThat(param.getDescription()).isEqualTo("The patient's identifier (MRN or other card number)");
				found = true;
			}
		}
		assertThat(found).isTrue();
		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertThat(conf).contains("<documentation value=\"The patient's identifier (MRN or other card number)\"/>");
		assertThat(conf).contains("<type value=\"token\"/>");

	}

	@Test
	public void testProviderWithRequiredAndOptional() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ProviderWithRequiredAndOptional());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		ConformanceRestComponent rest = conformance.getRest().get(0);
		ConformanceRestResourceComponent res = rest.getResource().get(0);
		assertThat(res.getType()).isEqualTo("DiagnosticReport");

		assertThat(res.getSearchParam().get(0).getName()).isEqualTo(DiagnosticReport.SP_SUBJECT);
		assertThat(res.getSearchParam().get(0).getChain().get(0).getValue()).isEqualTo("identifier");

		assertThat(res.getSearchParam().get(1).getName()).isEqualTo(DiagnosticReport.SP_CODE);

		assertThat(res.getSearchParam().get(2).getName()).isEqualTo(DiagnosticReport.SP_DATE);

		assertThat(res.getSearchInclude()).hasSize(1);
		assertThat(res.getSearchInclude().get(0).getValue()).isEqualTo("DiagnosticReport.result");
	}

	@Test
	public void testReadAndVReadSupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new VreadProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf).contains("<interaction><code value=\"vread\"/></interaction>");
		assertThat(conf).contains("<interaction><code value=\"read\"/></interaction>");
	}

	@Test
	public void testReadSupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new ReadProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf).doesNotContain("<interaction><code value=\"vread\"/></interaction>");
		assertThat(conf).contains("<interaction><code value=\"read\"/></interaction>");
	}

	@Test
	public void testSearchParameterDocumentation() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SearchProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		boolean found = false;
		Collection<ResourceBinding> resourceBindings = rs.getResourceBindings();
		for (ResourceBinding resourceBinding : resourceBindings) {
			if (resourceBinding.getResourceName().equals("Patient")) {
				List<BaseMethodBinding> methodBindings = resourceBinding.getMethodBindings();
				SearchMethodBinding binding = (SearchMethodBinding) methodBindings.get(0);
				SearchParameter param = (SearchParameter) binding.getParameters().iterator().next();
				assertThat(param.getDescription()).isEqualTo("The patient's identifier (MRN or other card number)");
				found = true;
			}
		}
		assertThat(found).isTrue();
		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		assertThat(conf).contains("<documentation value=\"The patient's identifier (MRN or other card number)\"/>");
		assertThat(conf).contains("<type value=\"token\"/>");

	}

	@Test
	public void testSystemHistorySupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new SystemHistoryProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf).contains("<interaction><code value=\"" + SystemRestfulInteraction.HISTORYSYSTEM.toCode() + "\"/></interaction>");
	}

	@Test
	public void testTypeHistorySupported() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new TypeHistoryProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));
		String conf = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(conformance);
		ourLog.info(conf);

		conf = ourCtx.newXmlParser().setPrettyPrint(false).encodeResourceToString(conformance);
		assertThat(conf).contains("<interaction><code value=\"" + TypeRestfulInteraction.HISTORYTYPE.toCode() + "\"/></interaction>");
	}

	@Test
	public void testValidateGeneratedStatement() throws Exception {

		RestfulServer rs = new RestfulServer(ourCtx);
		rs.setProviders(new MultiOptionalProvider());

		ServerConformanceProvider sc = new ServerConformanceProvider(rs);
		rs.setServerConformanceProvider(sc);

		rs.init(createServletConfig());

		Conformance conformance = sc.getServerConformance(createHttpServletRequest(), createRequestDetails(rs));

		assertThat(ourCtx.newValidator().validateWithResult(conformance).isSuccessful()).isTrue();
	}

	public static class ConditionalProvider implements IResourceProvider {

		@Create
		public MethodOutcome create(@ResourceParam Patient thePatient, @ConditionalUrlParam String theConditionalUrl) {
			return null;
		}

		@Delete
		public MethodOutcome delete(@IdParam IdType theId, @ConditionalUrlParam String theConditionalUrl) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@Update
		public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient, @ConditionalUrlParam String theConditionalUrl) {
			return null;
		}

	}

	public static class InstanceHistoryProvider implements IResourceProvider {
		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@History
		public List<IBaseResource> history(@IdParam IdType theId) {
			return null;
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class MultiOptionalProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier") @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier, @Description(shortDefinition = "The patient's name") @OptionalParam(name = Patient.SP_NAME) StringParam theName) {
			return null;
		}

	}

	public static class NonConditionalProvider implements IResourceProvider {

		@Create
		public MethodOutcome create(@ResourceParam Patient thePatient) {
			return null;
		}

		@Delete
		public MethodOutcome delete(@IdParam IdType theId) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@Update
		public MethodOutcome update(@IdParam IdType theId, @ResourceParam Patient thePatient) {
			return null;
		}

	}

	public static class PlainProviderWithExtendedOperationOnNoType {

		@Operation(name = "plain", idempotent = true, returnParameters= {
			@OperationParam(min=1, max=2, name="out1", type=StringType.class)
		})
		public IBundleProvider everything(jakarta.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId, @OperationParam(name = "start") DateType theStart, @OperationParam(name = "end") DateType theEnd) {
			return null;
		}

	}

	public static class ProviderWithExtendedOperationReturningBundle implements IResourceProvider {

		@Operation(name = "everything", idempotent = true)
		public IBundleProvider everything(jakarta.servlet.http.HttpServletRequest theServletRequest, @IdParam IdType theId, @OperationParam(name = "start") DateType theStart, @OperationParam(name = "end") DateType theEnd) {
			return null;
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

	}

	public static class ProviderWithRequiredAndOptional {

		@Description(shortDefinition = "This is a search for stuff!")
		@Search
		public List<DiagnosticReport> findDiagnosticReportsByPatient(@RequiredParam(name = DiagnosticReport.SP_SUBJECT + '.' + Patient.SP_IDENTIFIER) TokenParam thePatientId, @OptionalParam(name = DiagnosticReport.SP_CODE) TokenOrListParam theNames,
				@OptionalParam(name = DiagnosticReport.SP_DATE) DateRangeParam theDateRange, @IncludeParam(allow = { "DiagnosticReport.result" }) Set<Include> theIncludes) throws Exception {
			return null;
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class ReadProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
			return null;
		}

		@Read(version = false)
		public Patient readPatient(@IdParam IdType theId) {
			return null;
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class SearchProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
			return null;
		}

	}

	public static class SystemHistoryProvider {

		@History
		public List<IBaseResource> history() {
			return null;
		}

	}

	public static class TypeHistoryProvider implements IResourceProvider {

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Patient.class;
		}

		@History
		public List<IBaseResource> history() {
			return null;
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class VreadProvider {

		@Search(type = Patient.class)
		public Patient findPatient(@Description(shortDefinition = "The patient's identifier (MRN or other card number)") @RequiredParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier) {
			return null;
		}

		@Read(version = true)
		public Patient readPatient(@IdParam IdType theId) {
			return null;
		}

	}

  private RequestDetails createRequestDetails(RestfulServer theServer) {
    ServletRequestDetails retVal = new ServletRequestDetails();
    retVal.setServer(theServer);
    return retVal;
  }

}
