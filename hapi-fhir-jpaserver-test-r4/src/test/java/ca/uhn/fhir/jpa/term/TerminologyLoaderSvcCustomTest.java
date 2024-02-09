package ca.uhn.fhir.jpa.term;

import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.term.api.ITermCodeSystemStorageSvc;
import ca.uhn.fhir.jpa.term.api.ITermDeferredStorageSvc;
import ca.uhn.fhir.jpa.term.custom.CustomTerminologySet;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TerminologyLoaderSvcCustomTest extends BaseLoaderTest {
	private TermLoaderSvcImpl mySvc;

	@Mock
	private ITermCodeSystemStorageSvc myTermCodeSystemStorageSvc;
	private ZipCollectionBuilder myFiles;
	@Captor
	private ArgumentCaptor<CustomTerminologySet> myCustomTerminologySetCaptor;
	@Mock
	private ITermDeferredStorageSvc myTermDeferredStorageSvc;

	@BeforeEach
	public void before() {
		mySvc = TermLoaderSvcImpl.withoutProxyCheck(myTermDeferredStorageSvc, myTermCodeSystemStorageSvc);

		myFiles = new ZipCollectionBuilder();
	}

	@Test
	public void testLoadComplete() throws Exception {
		myFiles.addFileZip("/custom_term/", TermLoaderSvcImpl.CUSTOM_CODESYSTEM_JSON);
		myFiles.addFileZip("/custom_term/", TermLoaderSvcImpl.CUSTOM_CONCEPTS_FILE);
		myFiles.addFileZip("/custom_term/", TermLoaderSvcImpl.CUSTOM_HIERARCHY_FILE);

		// Actually do the load
		mySvc.loadCustom("http://example.com/labCodes", myFiles.getFiles(), mySrd);

		verify(myTermCodeSystemStorageSvc, times(1)).storeNewCodeSystemVersion(mySystemCaptor.capture(), myCsvCaptor.capture(), any(RequestDetails.class), myValueSetsCaptor.capture(), myConceptMapCaptor.capture());
		Map<String, TermConcept> concepts = extractConcepts();

		// Verify codesystem
		assertThat(mySystemCaptor.getValue().getUrl()).isEqualTo("http://example.com/labCodes");
		assertThat(mySystemCaptor.getValue().getContent()).isEqualTo(CodeSystem.CodeSystemContentMode.NOTPRESENT);
		assertThat(mySystemCaptor.getValue().getName()).isEqualTo("Example Lab Codes");

		// Root code
		TermConcept code;
		assertThat(concepts).hasSize(2);
		code = concepts.get("CHEM");
		assertThat(code.getCode()).isEqualTo("CHEM");
		assertThat(code.getDisplay()).isEqualTo("Chemistry");

		assertThat(code.getChildren()).hasSize(2);
		assertThat(code.getChildren().get(0).getChild().getCode()).isEqualTo("HB");
		assertThat(code.getChildren().get(0).getChild().getDisplay()).isEqualTo("Hemoglobin");
		assertThat(code.getChildren().get(1).getChild().getCode()).isEqualTo("NEUT");
		assertThat(code.getChildren().get(1).getChild().getDisplay()).isEqualTo("Neutrophils");

	}

	@Test
	public void testLoadWithNoCodeSystem() throws Exception {
		myFiles.addFileZip("/custom_term/", TermLoaderSvcImpl.CUSTOM_CONCEPTS_FILE);

		// Actually do the load
		mySvc.loadCustom("http://example.com/labCodes", myFiles.getFiles(), mySrd);

		verify(myTermCodeSystemStorageSvc, times(1)).storeNewCodeSystemVersion(mySystemCaptor.capture(), myCsvCaptor.capture(), any(RequestDetails.class), myValueSetsCaptor.capture(), myConceptMapCaptor.capture());
		Map<String, TermConcept> concepts = extractConcepts();

		// Verify codesystem
		assertThat(mySystemCaptor.getValue().getUrl()).isEqualTo("http://example.com/labCodes");
		assertThat(mySystemCaptor.getValue().getContent()).isEqualTo(CodeSystem.CodeSystemContentMode.NOTPRESENT);

	}

	/**
	 * No hierarchy file supplied
	 */
	@Test
	public void testLoadCodesOnly() throws Exception {
		myFiles.addFileZip("/custom_term/", TermLoaderSvcImpl.CUSTOM_CONCEPTS_FILE);

		// Actually do the load
		mySvc.loadCustom("http://example.com/labCodes", myFiles.getFiles(), mySrd);

		verify(myTermCodeSystemStorageSvc, times(1)).storeNewCodeSystemVersion(mySystemCaptor.capture(), myCsvCaptor.capture(), any(RequestDetails.class), myValueSetsCaptor.capture(), myConceptMapCaptor.capture());
		Map<String, TermConcept> concepts = extractConcepts();

		TermConcept code;

		// Root code
		assertThat(concepts).hasSize(5);
		code = concepts.get("CHEM");
		assertThat(code.getCode()).isEqualTo("CHEM");
		assertThat(code.getDisplay()).isEqualTo("Chemistry");

	}

	@Test
	public void testDeltaAdd() throws IOException {

		myFiles.addFileText(loadResource("/custom_term/concepts.csv"), "concepts.csv");
		myFiles.addFileText(loadResource("/custom_term/hierarchy.csv"), "hierarchy.csv");

		UploadStatistics stats = new UploadStatistics(100, new IdType("CodeSystem/100"));
		when(myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd(eq("http://foo/system"), any())).thenReturn(stats);

		UploadStatistics outcome = mySvc.loadDeltaAdd("http://foo/system", myFiles.getFiles(), mySrd);
		assertThat(outcome).isSameAs(stats);

		verify(myTermCodeSystemStorageSvc, times(1)).applyDeltaCodeSystemsAdd(eq("http://foo/system"), myCustomTerminologySetCaptor.capture());
		CustomTerminologySet set = myCustomTerminologySetCaptor.getValue();

		// Root concepts
		assertThat(set.getRootConcepts()).hasSize(2);
		assertThat(set.getRootConcepts().get(0).getCode()).isEqualTo("CHEM");
		assertThat(set.getRootConcepts().get(0).getDisplay()).isEqualTo("Chemistry");
		assertThat(set.getRootConcepts().get(1).getCode()).isEqualTo("MICRO");
		assertThat(set.getRootConcepts().get(1).getDisplay()).isEqualTo("Microbiology");

		// Child concepts
		assertThat(set.getRootConcepts().get(0).getChildren()).hasSize(2);
		assertThat(set.getRootConcepts().get(0).getChildren().get(0).getChild().getCode()).isEqualTo("HB");
		assertThat(set.getRootConcepts().get(0).getChildren().get(0).getChild().getDisplay()).isEqualTo("Hemoglobin");
		assertThat(set.getRootConcepts().get(0).getChildren().get(0).getChild().getSequence()).isEqualTo(null);
		assertThat(set.getRootConcepts().get(0).getChildren().get(1).getChild().getCode()).isEqualTo("NEUT");
		assertThat(set.getRootConcepts().get(0).getChildren().get(1).getChild().getDisplay()).isEqualTo("Neutrophils");

	}

	@Test
	public void testDeltaRemove() throws IOException {

		myFiles.addFileText(loadResource("/custom_term/concepts.csv"), "concepts.csv");

		// Hierarchy should be ignored for remove, but we'll add one just
		// to make sure it's ignored..
		myFiles.addFileText(loadResource("/custom_term/hierarchy.csv"), "hierarchy.csv");

		UploadStatistics stats = new UploadStatistics(100, new IdType("CodeSystem/100"));
		when(myTermCodeSystemStorageSvc.applyDeltaCodeSystemsRemove(eq("http://foo/system"), any())).thenReturn(stats);

		UploadStatistics outcome = mySvc.loadDeltaRemove("http://foo/system", myFiles.getFiles(), mySrd);
		assertThat(outcome).isSameAs(stats);

		verify(myTermCodeSystemStorageSvc, times(1)).applyDeltaCodeSystemsRemove(eq("http://foo/system"), myCustomTerminologySetCaptor.capture());
		CustomTerminologySet set = myCustomTerminologySetCaptor.getValue();

		// Root concepts
		assertThat(set.getRootConcepts()).hasSize(5);
		assertThat(set.getRootConcepts().get(0).getCode()).isEqualTo("CHEM");
		assertThat(set.getRootConcepts().get(0).getDisplay()).isEqualTo("Chemistry");
		assertThat(set.getRootConcepts().get(1).getCode()).isEqualTo("HB");
		assertThat(set.getRootConcepts().get(1).getDisplay()).isEqualTo("Hemoglobin");
		assertThat(set.getRootConcepts().get(2).getCode()).isEqualTo("NEUT");
		assertThat(set.getRootConcepts().get(2).getDisplay()).isEqualTo("Neutrophils");

	}


}
