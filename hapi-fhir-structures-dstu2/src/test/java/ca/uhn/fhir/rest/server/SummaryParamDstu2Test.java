package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.MedicationOrder;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.MaritalStatusCodesEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.SummaryEnum;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class SummaryParamDstu2Test {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(SummaryParamDstu2Test.class);
	private static final FhirContext ourCtx = FhirContext.forDstu2Cached();
	private static SummaryEnum ourLastSummary;
	private static List<SummaryEnum> ourLastSummaryList;

	@RegisterExtension
	public static final RestfulServerExtension ourServer  = new RestfulServerExtension(ourCtx)
		.setDefaultResponseEncoding(EncodingEnum.XML)
		.registerProvider(new DummyPatientResourceProvider())
		.registerProvider(new DummyMedicationOrderProvider())
		.withPagingProvider(new FifoMemoryPagingProvider(100))
		.setDefaultPrettyPrint(false);

	@RegisterExtension
	public static final HttpClientExtension ourClient = new HttpClientExtension();

	@BeforeEach
	public void before() {
		ourLastSummary = null;
		ourLastSummaryList = null;
	}

	@Test
	public void testReadSummaryData() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/1?_summary=" + SummaryEnum.DATA.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(status.getEntity().getContentType().getValue().replace(" ", "").replace("UTF", "utf")).isEqualTo(Constants.CT_FHIR_XML + Constants.CHARSET_UTF8_CTSUFFIX.replace(" ", "").toLowerCase());
		assertThat(responseContent).doesNotContain("<Bundle");
		assertThat(responseContent, (containsString("<Patien")));
		assertThat(responseContent).doesNotContain("<div>THE DIV</div>");
		assertThat(responseContent, (containsString("family")));
		assertThat(responseContent, (containsString("maritalStatus")));
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.DATA);
	}


	@Test
	public void testReadSummaryText() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/1?_summary=" + SummaryEnum.TEXT.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(status.getEntity().getContentType().getValue().replace(" ", "").replace("UTF", "utf")).isEqualTo(Constants.CT_HTML_WITH_UTF8.replace(" ", "").toLowerCase());
		assertThat(responseContent).doesNotContain("<Bundle");
		assertThat(responseContent).doesNotContain("<Medic");
		assertThat(responseContent).isEqualTo("<div xmlns=\"http://www.w3.org/1999/xhtml\">THE DIV</div>");
		assertThat(responseContent).doesNotContain("efer");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.TEXT);
	}

	@Test
	public void testReadSummaryTextWithMandatory() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/MedicationOrder/1?_summary=" + SummaryEnum.TEXT.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(status.getEntity().getContentType().getValue().replace(" ", "").replace("UTF", "utf")).isEqualTo(Constants.CT_HTML_WITH_UTF8.replace(" ", "").toLowerCase());
		assertThat(responseContent).doesNotContain("<Bundle");
		assertThat(responseContent).doesNotContain("<Patien");
		assertThat(responseContent).isEqualTo("<div xmlns=\"http://www.w3.org/1999/xhtml\">TEXT</div>");
		assertThat(responseContent).doesNotContain("family");
		assertThat(responseContent).doesNotContain("maritalStatus");
	}

	@Test
	public void testReadSummaryTrue() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/1?_summary=" + SummaryEnum.TRUE.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(status.getEntity().getContentType().getValue().replace(" ", "").replace("UTF", "utf")).isEqualTo(Constants.CT_FHIR_XML + Constants.CHARSET_UTF8_CTSUFFIX.replace(" ", "").toLowerCase());
		assertThat(responseContent).doesNotContain("<Bundle");
		assertThat(responseContent, (containsString("<Patien")));
		assertThat(responseContent).doesNotContain("<div>THE DIV</div>");
		assertThat(responseContent, (containsString("family")));
		assertThat(responseContent).doesNotContain("maritalStatus");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.TRUE);
	}

	@Test
	public void testSearchSummaryCount() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_pretty=true&_summary=" + SummaryEnum.COUNT.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent, (containsString("<total value=\"1\"/>")));
		assertThat(responseContent).doesNotContain("entry");
		assertThat(responseContent).doesNotContain("THE DIV");
		assertThat(responseContent).doesNotContain("family");
		assertThat(responseContent).doesNotContain("maritalStatus");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.COUNT);
	}

	@Test
	public void testSearchSummaryData() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_summary=" + SummaryEnum.DATA.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent).contains("<Patient");
		assertThat(responseContent).doesNotContain("THE DIV");
		assertThat(responseContent).contains("family");
		assertThat(responseContent).contains("maritalStatus");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.DATA);
	}

	@Test
	public void testSearchSummaryFalse() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_summary=false");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent).contains("<Patient");
		assertThat(responseContent).contains("THE DIV");
		assertThat(responseContent).contains("family");
		assertThat(responseContent).contains("maritalStatus");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.FALSE);
	}

	@Test
	public void testSearchSummaryText() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_summary=" + SummaryEnum.TEXT.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent, (containsString("<total value=\"1\"/>")));
		assertThat(responseContent, (containsString("entry")));
		assertThat(responseContent, (containsString("THE DIV")));
		assertThat(responseContent).doesNotContain("family");
		assertThat(responseContent).doesNotContain("maritalStatus");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.TEXT);
	}

	@Test
	public void testSearchSummaryTextWithMandatory() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/MedicationOrder?_summary=" + SummaryEnum.TEXT.getCode() + "&_pretty=true");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent, (containsString("<total value=\"1\"/>")));
		assertThat(responseContent, (containsString("entry")));
		assertThat(responseContent, (containsString(">TEXT<")));
		assertThat(responseContent, (containsString("Medication/123")));
		assertThat(responseContent).doesNotContainIgnoringCase("note");
	}

	@Test
	public void testSearchSummaryTextMulti() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_query=multi&_summary=" + SummaryEnum.TEXT.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent, (containsString("<total value=\"1\"/>")));
		assertThat(responseContent, (containsString("entry")));
		assertThat(responseContent, (containsString("THE DIV")));
		assertThat(responseContent).doesNotContain("family");
		assertThat(responseContent).doesNotContain("maritalStatus");
		assertThat(ourLastSummaryList).containsExactly(SummaryEnum.TEXT);
	}

	@Test
	public void testSearchSummaryTrue() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_summary=" + SummaryEnum.TRUE.getCode());
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		assertThat(responseContent).contains("<Patient");
		assertThat(responseContent).doesNotContain("THE DIV");
		assertThat(responseContent).contains("family");
		assertThat(responseContent).doesNotContain("maritalStatus");
		assertThat(ourLastSummary).isEqualTo(SummaryEnum.TRUE);
	}

	@Test
	public void testSearchSummaryWithTextAndOthers() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient?_summary=text&_summary=data");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(responseContent);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(400);
		assertThat(responseContent).contains("Can not combine _summary=text with other values for _summary");
	}

	public static class DummyMedicationOrderProvider implements IResourceProvider {

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return MedicationOrder.class;
		}

		@Read
		public MedicationOrder read(@IdParam IdDt theId) {
			MedicationOrder retVal = new MedicationOrder();
			retVal.getText().setDiv("<div>TEXT</div>");
			retVal.getNoteElement().setValue("NOTE");
			retVal.setMedication(new ResourceReferenceDt("Medication/123"));
			retVal.setId(theId);
			return retVal;
		}

		@Search
		public List<MedicationOrder> read() {
			return Arrays.asList(read(new IdDt("999")));
		}

	}

	private static class DummyPatientResourceProvider implements IResourceProvider {

		@Override
		public Class<? extends IResource> getResourceType() {
			return Patient.class;
		}

		@Read
		public Patient read(@IdParam IdDt theId, SummaryEnum theSummary) {
			ourLastSummary = theSummary;
			Patient patient = new Patient();
			patient.setId("Patient/1/_history/1");
			patient.getText().setDiv("<div>THE DIV</div>");
			patient.addName().addFamily("FAMILY");
			patient.setMaritalStatus(MaritalStatusCodesEnum.D);
			return patient;
		}

		@Search(queryName = "multi")
		public Patient search(List<SummaryEnum> theSummary) {
			ourLastSummaryList = theSummary;
			Patient patient = new Patient();
			patient.setId("Patient/1/_history/1");
			patient.getText().setDiv("<div>THE DIV</div>");
			patient.addName().addFamily("FAMILY");
			patient.setMaritalStatus(MaritalStatusCodesEnum.D);
			return patient;
		}

		@Search()
		public Patient search(SummaryEnum theSummary) {
			ourLastSummary = theSummary;
			Patient patient = new Patient();
			patient.setId("Patient/1/_history/1");
			patient.getText().setDiv("<div>THE DIV</div>");
			patient.addName().addFamily("FAMILY");
			patient.setMaritalStatus(MaritalStatusCodesEnum.D);
			return patient;
		}

	}

	@AfterAll
	public static void afterClassClearContext() throws Exception {
		TestUtil.randomizeLocaleAndTimezone();
	}


}
