package ca.uhn.fhir.jpa.term;

import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.LookupCodeRequest;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.entity.TermConcept;
import ca.uhn.fhir.jpa.entity.TermConceptParentChildLink;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.term.api.ITermDeferredStorageSvc;
import ca.uhn.fhir.jpa.term.custom.CustomTerminologySet;
import ca.uhn.fhir.jpa.test.BaseJpaR4Test;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.leftPad;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.fail;


public class TerminologySvcDeltaR4Test extends BaseJpaR4Test {
	private static final Logger ourLog = LoggerFactory.getLogger(TerminologySvcDeltaR4Test.class);

	@AfterEach
	public void after() {
		myStorageSettings.setDeferIndexingForCodesystemsOfSize(new JpaStorageSettings().getDeferIndexingForCodesystemsOfSize());
		TermDeferredStorageSvcImpl termDeferredStorageSvc = AopTestUtils.getTargetObject(myTermDeferredStorageSvc);
		termDeferredStorageSvc.clearDeferred();
	}

	@Test
	public void testAddRootConcepts() {
		createNotPresentCodeSystem();
		ValueSet vs;
		vs = expandNotPresentCodeSystem();
		assertThat(vs.getExpansion().getContains()).isEmpty();

		CustomTerminologySet delta = new CustomTerminologySet();
		delta.addRootConcept("RootA", "Root A");
		delta.addRootConcept("RootB", "Root B");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			"RootB seq=0"
		);

		delta = new CustomTerminologySet();
		delta.addRootConcept("RootC", "Root C");
		delta.addRootConcept("RootD", "Root D");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			"RootB seq=0",
			"RootC seq=0",
			"RootD seq=0"
		);
	}

	@Test
	public void testAddRootConceptsWithCycle() {
		createNotPresentCodeSystem();
		ValueSet vs;
		vs = expandNotPresentCodeSystem();
		assertThat(vs.getExpansion().getContains()).isEmpty();

		CustomTerminologySet delta = new CustomTerminologySet();

		TermConcept root = delta.addRootConcept("Root", "Root");
		TermConcept child = root.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("Child").setDisplay("Child");
		child.addChild(root, TermConceptParentChildLink.RelationshipTypeEnum.ISA);

		try {
			myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
			fail("");
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage()).isEqualTo(Msg.code(926) + "Cycle detected around code Root");
		}
	}

	@Test
	public void testAddHierarchyConcepts() {
		ourLog.info("Starting testAddHierarchyConcepts");

		createNotPresentCodeSystem();
		assertHierarchyContains();

		ourLog.info("Have created code system");
		runInTransaction(() -> {
			ourLog.info("All code systems: {}", myTermCodeSystemDao.findAll());
			ourLog.info("All code system versions: {}", myTermCodeSystemVersionDao.findAll());
			ourLog.info("All concepts: {}", myTermConceptDao.findAll());
		});

		CustomTerminologySet delta = new CustomTerminologySet();
		delta.addRootConcept("RootA", "Root A");
		delta.addRootConcept("RootB", "Root B");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			"RootB seq=0"
		);

		ourLog.info("Have performed add");
		runInTransaction(() -> {
			ourLog.info("All code systems: {}", myTermCodeSystemDao.findAll());
			ourLog.info("All code system versions: {}", myTermCodeSystemVersionDao.findAll());
			ourLog.info("All concepts: {}", myTermConceptDao.findAll());
		});

		myCaptureQueriesListener.clear();

		delta = new CustomTerminologySet();
		TermConcept root = delta.addRootConcept("RootA", "Root A");
		root.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA");
		root.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAB").setDisplay("Child AB");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);

		myCaptureQueriesListener.logAllQueriesForCurrentThread();

		assertHierarchyContains(
			"RootA seq=0",
			" ChildAA seq=0",
			" ChildAB seq=1",
			"RootB seq=0"
		);

	}

	@Test
	public void testAddMoveConceptFromOneParentToAnother() {
		createNotPresentCodeSystem();
		assertHierarchyContains();

		UploadStatistics outcome;
		CustomTerminologySet delta;

		delta = new CustomTerminologySet();
		delta.addRootConcept("RootA", "Root A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAAA").setDisplay("Child AAA");
		delta.addRootConcept("RootB", "Root B");
		outcome = myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			" ChildAA seq=0",
			"  ChildAAA seq=0",
			"RootB seq=0"
		);
		assertThat(outcome.getUpdatedConceptCount()).isEqualTo(4);

		delta = new CustomTerminologySet();
		delta.addRootConcept("RootB", "Root B")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA");
		outcome = myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			" ChildAA seq=0",
			"  ChildAAA seq=0",
			"RootB seq=0",
			" ChildAA seq=0",
			"  ChildAAA seq=0"
		);
		assertThat(outcome.getUpdatedConceptCount()).isEqualTo(2);

		runInTransaction(() -> {
			TermConcept concept = myTermSvc.findCode("http://foo/cs", "ChildAA").orElseThrow(() -> new IllegalStateException());
			assertEquals(2, concept.getParents().size());
			assertThat(concept.getParentPidsAsString()).matches("^[0-9]+ [0-9]+$");
		});

	}

	@Test
	public void testReAddingConceptsDoesntRecreateExistingLinks() {
		createNotPresentCodeSystem();
		assertHierarchyContains();

		UploadStatistics outcome;
		CustomTerminologySet delta;

		myCaptureQueriesListener.clear();

		delta = new CustomTerminologySet();
		delta.addRootConcept("RootA", "Root A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			" ChildAA seq=0"
		);

		myCaptureQueriesListener.logDeleteQueries();
		assertThat(myCaptureQueriesListener.countDeleteQueries()).isEqualTo(0);
		myCaptureQueriesListener.logInsertQueries();
		// 2 concepts, 1 link
		assertThat(myCaptureQueriesListener.countInsertQueries()).isEqualTo(3);
		myCaptureQueriesListener.clear();

		delta = new CustomTerminologySet();
		delta.addRootConcept("RootA", "Root A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAAA").setDisplay("Child AAA");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			" ChildAA seq=0",
			"  ChildAAA seq=0"
		);

		myCaptureQueriesListener.logDeleteQueries();
		assertThat(myCaptureQueriesListener.countDeleteQueries()).isEqualTo(0);
		myCaptureQueriesListener.logInsertQueries();
		// 1 concept, 1 link
		assertThat(myCaptureQueriesListener.countInsertQueries()).isEqualTo(2);
		myCaptureQueriesListener.clear();

		delta = new CustomTerminologySet();
		delta.addRootConcept("RootA", "Root A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAAA").setDisplay("Child AAA")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAAAA").setDisplay("Child AAAA");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);
		assertHierarchyContains(
			"RootA seq=0",
			" ChildAA seq=0",
			"  ChildAAA seq=0",
			"   ChildAAAA seq=0"
		);

		myCaptureQueriesListener.logDeleteQueries();
		assertThat(myCaptureQueriesListener.countDeleteQueries()).isEqualTo(0);
		myCaptureQueriesListener.logInsertQueries();
		// 1 concept, 1 link
		assertThat(myCaptureQueriesListener.countInsertQueries()).isEqualTo(2);
		myCaptureQueriesListener.clear();

	}

	@Test
	public void testAddNotPermittedForNonExternalCodeSystem() {

		// Create not-present
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://foo");
		cs.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
		myCodeSystemDao.create(cs);

		try {
			myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", new CustomTerminologySet());
			fail("");
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage()).contains("can not apply a delta - wrong content mode");
		}

	}

	@Test
	public void testAddChildToExistingChild() {
		CustomTerminologySet set;

		// Create not-present
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://foo");
		cs.setContent(CodeSystem.CodeSystemContentMode.NOTPRESENT);
		myCodeSystemDao.create(cs);

		// Add parent with 1 child
		set = new CustomTerminologySet();
		set.addRootConcept("ParentA", "Parent A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildA").setDisplay("Child A");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", set);

		// Check so far
		assertHierarchyContains(
			"ParentA seq=0",
			" ChildA seq=0"
		);

		// Add sub-child to existing child
		ourLog.info("*** Adding child to existing child");
		set = new CustomTerminologySet();
		set.addRootConcept("ChildA", "Child A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay("Child AA");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", set);

		// Check so far
		assertHierarchyContains(
			"ParentA seq=0",
			" ChildA seq=0",
			"  ChildAA seq=0"
		);

	}

	@Test
	public void testAddChildWithVeryLongDescription() {
		CustomTerminologySet set;

		// Create not-present
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://foo");
		cs.setContent(CodeSystem.CodeSystemContentMode.NOTPRESENT);
		myCodeSystemDao.create(cs);

		// Add parent with 1 child
		set = new CustomTerminologySet();
		set.addRootConcept("ParentA", "Parent A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildA").setDisplay("Child A");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", set);

		// Check so far
		assertHierarchyContains(
			"ParentA seq=0",
			" ChildA seq=0"
		);

		// Add sub-child to existing child
		ourLog.info("*** Adding child to existing child");
		set = new CustomTerminologySet();
		set.addRootConcept("ChildA", "Child A")
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode("ChildAA").setDisplay(leftPad("", 10000, 'Z'));
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", set);

		// Check so far
		assertHierarchyContains(
			"ParentA seq=0",
			" ChildA seq=0",
			"  ChildAA seq=0"
		);

	}

	@Test
	public void testAddWithoutPreExistingCodeSystem() {

		CustomTerminologySet delta = new CustomTerminologySet();
		delta.addRootConcept("CBC", "Complete Blood Count");
		delta.addRootConcept("URNL", "Routine Urinalysis");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", delta);

		SearchParameterMap params = new SearchParameterMap();
		params.setLoadSynchronous(true);
		params.add(CodeSystem.SP_URL, new UriParam("http://foo"));
		IBundleProvider searchResult = myCodeSystemDao.search(params, mySrd);
		assertThat(Objects.requireNonNull(searchResult.size()).intValue()).isEqualTo(1);
		CodeSystem outcome = (CodeSystem) searchResult.getResources(0, 1).get(0);

		assertThat(outcome.getUrl()).isEqualTo("http://foo");
		assertThat(outcome.getContent()).isEqualTo(CodeSystem.CodeSystemContentMode.NOTPRESENT);

		IValidationSupport.LookupCodeResult lookup = myTermSvc.lookupCode(new ValidationSupportContext(myValidationSupport), new LookupCodeRequest("http://foo", "CBC"));
		assertThat(lookup.getCodeDisplay()).isEqualTo("Complete Blood Count");
	}


	@Test
	public void testAddLargeHierarchy() {
		myStorageSettings.setDeferIndexingForCodesystemsOfSize(5);

		createNotPresentCodeSystem();
		ValueSet vs;
		vs = expandNotPresentCodeSystem();
		assertThat(vs.getExpansion().getContains()).isEmpty();

		CustomTerminologySet delta = new CustomTerminologySet();

		// Create a nice deep hierarchy
		TermConcept concept = delta.addRootConcept("Root", "Root");
		int nestedDepth = 10;
		for (int i = 0; i < nestedDepth; i++) {
			String name = concept.getCode();
			concept = concept.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA).setCode(name + "0").setDisplay(name + "0");
		}

		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);

		assertThat(myTermDeferredStorageSvc.isStorageQueueEmpty(true)).isFalse();
		int counter = 0;
		while (!myTermDeferredStorageSvc.isStorageQueueEmpty(true) && ++counter < 10000) {
			myTermDeferredStorageSvc.saveDeferred();
		}
		if (counter >= 10000) {
			ourLog.error("Failed to empty myTermDeferredStorageSvc storage queue after 10000 attempts");
			myTermDeferredStorageSvc.logQueueForUnitTest();
			fail("myTermDeferredStorageSvc.saveDeferred() did not empty myTermDeferredStorageSvc storage queue.");
		}

		List<String> expectedHierarchy = new ArrayList<>();
		for (int i = 0; i < nestedDepth + 1; i++) {
			String expected = leftPad("", i, " ") +
				"Root" +
				leftPad("", i, "0") +
				" seq=0";
			expectedHierarchy.add(expected);
		}

		assertHierarchyContains(expectedHierarchy.toArray(new String[0]));

	}

	@Autowired
	private ITermDeferredStorageSvc myTermDeferredStorageSvc;


	@Test
	public void testAddModifiesExistingCodesInPlace() {

		// Add codes
		CustomTerminologySet delta = new CustomTerminologySet();
		delta.addRootConcept("codea", "CODEA0");
		delta.addRootConcept("codeb", "CODEB0");

		UploadStatistics outcome = myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", delta);
		assertThat(outcome.getUpdatedConceptCount()).isEqualTo(2);
		assertThat(myTermSvc.lookupCode(new ValidationSupportContext(myValidationSupport),
			new LookupCodeRequest("http://foo", "codea")).getCodeDisplay()).isEqualTo("CODEA0");

		// Add codes again with different display
		delta = new CustomTerminologySet();
		delta.addRootConcept("codea", "CODEA1");
		delta.addRootConcept("codeb", "CODEB1");
		outcome = myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", delta);
		assertThat(outcome.getUpdatedConceptCount()).isEqualTo(2);
		assertThat(myTermSvc.lookupCode(new ValidationSupportContext(myValidationSupport),
			new LookupCodeRequest("http://foo", "codea")).getCodeDisplay()).isEqualTo("CODEA1");

		// Add codes again with no changes
		outcome = myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo", delta);
		assertThat(outcome.getUpdatedConceptCount()).isEqualTo(2);
		assertThat(myTermSvc.lookupCode(new ValidationSupportContext(myValidationSupport),
			new LookupCodeRequest("http://foo", "codea")).getCodeDisplay()).isEqualTo("CODEA1");
	}

	@Test
	public void testAddWithPropertiesAndDesignations() {

		// Create not-present
		CodeSystem cs = new CodeSystem();
		cs.setName("Description of my life");
		cs.setUrl("http://foo/cs");
		cs.setContent(CodeSystem.CodeSystemContentMode.NOTPRESENT);
		cs.setVersion("1.2.3");

		CodeSystem.ConceptDefinitionComponent concept = cs
			.addConcept()
			.setCode("lunch")
			.setDisplay("I'm having dog food");
		concept
			.addDesignation()
			.setLanguage("fr")
			.setUse(new Coding("http://sys", "code", "display"))
			.setValue("Je mange une pomme");
		concept
			.addDesignation()
			.setLanguage("es")
			.setUse(new Coding("http://sys", "code", "display"))
			.setValue("Como una pera");
		concept.addProperty()
			.setCode("flavour")
			.setValue(new StringType("Hints of lime"));
		concept.addProperty()
			.setCode("useless_sct_code")
			.setValue(new Coding("http://snomed.info", "1234567", "Choked on large meal (finding)"));
		myCodeSystemDao.create(cs, mySrd);

		IValidationSupport.LookupCodeResult result = myTermSvc.lookupCode(new ValidationSupportContext(myValidationSupport),
				new LookupCodeRequest("http://foo/cs", "lunch"));
		assertThat(result.isFound()).isEqualTo(true);
		assertThat(result.getSearchedForCode()).isEqualTo("lunch");
		assertThat(result.getSearchedForSystem()).isEqualTo("http://foo/cs");

		Parameters output = (Parameters) result.toParameters(myFhirContext, null);
		ourLog.debug(myFhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(output));

		assertThat(((StringType) output.getParameterValue("name")).getValue()).isEqualTo("Description of my life");
		assertThat(((StringType) output.getParameterValue("version")).getValue()).isEqualTo("1.2.3");
		assertThat(output.getParameterBool("abstract")).isEqualTo(false);

		List<Parameters.ParametersParameterComponent> designations = output.getParameter().stream().filter(t -> t.getName().equals("designation")).collect(Collectors.toList());
		assertThat(designations.get(0).getPart().get(0).getName()).isEqualTo("language");
		assertThat(((CodeType) designations.get(0).getPart().get(0).getValue()).getValueAsString()).isEqualTo("fr");
		assertThat(designations.get(0).getPart().get(1).getName()).isEqualTo("use");
		assertThat(((Coding) designations.get(0).getPart().get(1).getValue()).getSystem()).isEqualTo("http://sys");
		assertThat(((Coding) designations.get(0).getPart().get(1).getValue()).getCode()).isEqualTo("code");
		assertThat(((Coding) designations.get(0).getPart().get(1).getValue()).getDisplay()).isEqualTo("display");
		assertThat(designations.get(0).getPart().get(2).getName()).isEqualTo("value");
		assertThat(((StringType) designations.get(0).getPart().get(2).getValue()).getValueAsString()).isEqualTo("Je mange une pomme");

		List<Parameters.ParametersParameterComponent> properties = output.getParameter().stream().filter(t -> t.getName().equals("property")).collect(Collectors.toList());
		assertThat(properties.get(0).getPart().get(0).getName()).isEqualTo("code");
		assertThat(((StringType) properties.get(0).getPart().get(0).getValue()).getValueAsString()).isEqualTo("flavour");
		assertThat(properties.get(0).getPart().get(1).getName()).isEqualTo("value");
		assertThat(((StringType) properties.get(0).getPart().get(1).getValue()).getValueAsString()).isEqualTo("Hints of lime");

		assertThat(properties.get(1).getPart().get(0).getName()).isEqualTo("code");
		assertThat(((CodeType) properties.get(1).getPart().get(0).getValue()).getValueAsString()).isEqualTo("useless_sct_code");
		assertThat(properties.get(1).getPart().get(1).getName()).isEqualTo("value");
		assertThat(((Coding) properties.get(1).getPart().get(1).getValue()).getSystem()).isEqualTo("http://snomed.info");
		assertThat(((Coding) properties.get(1).getPart().get(1).getValue()).getCode()).isEqualTo("1234567");
		assertThat(((Coding) properties.get(1).getPart().get(1).getValue()).getDisplay()).isEqualTo("Choked on large meal (finding)");

	}

	@Test
	public void testRemove() {

		// Create not-present
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://foo/cs");
		cs.setContent(CodeSystem.CodeSystemContentMode.NOTPRESENT);
		myCodeSystemDao.create(cs);

		CustomTerminologySet delta = new CustomTerminologySet();
		TermConcept codeA = delta.addRootConcept("codeA", "displayA");
		TermConcept codeAA = codeA
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA)
			.setCode("codeAA")
			.setDisplay("displayAA");
		codeAA
			.addChild(TermConceptParentChildLink.RelationshipTypeEnum.ISA)
			.setCode("codeAAA")
			.setDisplay("displayAAA");
		delta.addRootConcept("codeB", "displayB");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsAdd("http://foo/cs", delta);

		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeB").isPresent())).isEqualTo(true);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeA").isPresent())).isEqualTo(true);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeAA").isPresent())).isEqualTo(true);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeAAA").isPresent())).isEqualTo(true);

		// Remove CodeB
		delta = new CustomTerminologySet();
		delta.addRootConcept("codeB", "displayB");
		myTermCodeSystemStorageSvc.applyDeltaCodeSystemsRemove("http://foo/cs", delta);

		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeB").isPresent())).isEqualTo(false);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeA").isPresent())).isEqualTo(true);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeAA").isPresent())).isEqualTo(true);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeAAA").isPresent())).isEqualTo(true);

		// Remove CodeA
		runInTransaction(() -> {
			ourLog.info("About to remove CodeA. Have codes:\n * {}", myTermConceptDao.findAll().stream().map(t -> t.toString()).collect(Collectors.joining("\n * ")));
		});


		myCaptureQueriesListener.clear();
		runInTransaction(() -> {
			CustomTerminologySet delta2 = new CustomTerminologySet();
			delta2.addRootConcept("codeA");
			myTermCodeSystemStorageSvc.applyDeltaCodeSystemsRemove("http://foo/cs", delta2);
		});
		myCaptureQueriesListener.logAllQueries();

		runInTransaction(() -> {
			ourLog.info("Done removing. Have codes:\n * {}", myTermConceptDao.findAll().stream().map(t -> t.toString()).collect(Collectors.joining("\n * ")));
		});

		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeB").isPresent())).isEqualTo(false);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeA").isPresent())).isEqualTo(false);
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeAA").isPresent())).isEqualTo(false); //TODO GGG JA this assert fails. If you swap to `deleteByPid` it does not fail.
		assertThat(runInTransaction(() -> myTermSvc.findCode("http://foo/cs", "codeAAA").isPresent())).isEqualTo(false);//And I assume this one does too.

	}


	@Test
	public void testRemove_UnknownSystem() {

		CustomTerminologySet delta = new CustomTerminologySet();
		delta.addRootConcept("codeA", "displayA");
		try {
			myTermCodeSystemStorageSvc.applyDeltaCodeSystemsRemove("http://foo/cs", delta);
		} catch (InvalidRequestException e) {
			assertThat(e.getMessage()).contains("Unknown code system: http://foo");
		}

	}


	private ValueSet expandNotPresentCodeSystem() {
		ValueSet vs = new ValueSet();
		vs.setUrl("http://foo/vs");
		vs.getCompose().addInclude().setSystem("http://foo/cs");
		vs = myValueSetDao.expand(vs, null);
		ourLog.debug(myFhirContext.newXmlParser().setPrettyPrint(true).encodeResourceToString(vs));
		return vs;
	}

	private void createNotPresentCodeSystem() {
		CodeSystem cs = new CodeSystem();
		cs.setUrl("http://foo/cs");
		cs.setContent(CodeSystem.CodeSystemContentMode.NOTPRESENT);
		myCodeSystemDao.create(cs);
	}

}
