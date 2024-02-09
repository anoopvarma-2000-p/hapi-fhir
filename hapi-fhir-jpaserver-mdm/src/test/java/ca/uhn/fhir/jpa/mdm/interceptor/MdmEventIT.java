package ca.uhn.fhir.jpa.mdm.interceptor;

import ca.uhn.fhir.jpa.entity.MdmLink;
import ca.uhn.fhir.jpa.mdm.BaseMdmR4Test;
import ca.uhn.fhir.jpa.mdm.helper.MdmHelperConfig;
import ca.uhn.fhir.jpa.mdm.helper.MdmHelperR4;
import ca.uhn.fhir.mdm.model.mdmevents.MdmLinkEvent;
import ca.uhn.fhir.mdm.model.mdmevents.MdmLinkJson;
import ca.uhn.fhir.mdm.api.MdmMatchResultEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.server.messaging.ResourceOperationMessage;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.slf4j.LoggerFactory.getLogger;

@TestPropertySource(properties = {
	"mdm.prevent_multiple_eids=false"
})
@ContextConfiguration(classes = {MdmHelperConfig.class})
public class MdmEventIT extends BaseMdmR4Test {

	private static final Logger ourLog = getLogger(MdmEventIT.class);

	@RegisterExtension
	@Autowired
	public MdmHelperR4 myMdmHelper;

	@Test
	public void testDuplicateLinkChangeEvent() throws InterruptedException {
		Patient patient1 = buildJanePatient();
		addExternalEID(patient1, "eid-1");
		addExternalEID(patient1, "eid-11");
		myMdmHelper.createWithLatch(patient1);

		Patient patient2 = buildPaulPatient();
		addExternalEID(patient2, "eid-2");
		addExternalEID(patient2, "eid-22");
		myMdmHelper.createWithLatch(patient2);

		Patient patient3 = buildPaulPatient();
		addExternalEID(patient3, "eid-22");
		myMdmHelper.createWithLatch(patient3);

		patient2.getIdentifier().clear();
		addExternalEID(patient2, "eid-11");
		addExternalEID(patient2, "eid-22");

		MdmHelperR4.OutcomeAndLogMessageWrapper outcome = myMdmHelper.updateWithLatch(patient2);

		MdmLinkEvent linkChangeEvent = outcome.getMdmLinkEvent();
		assertThat(linkChangeEvent).isNotNull();

		ourLog.info("Got event: {}", linkChangeEvent);

		long expectTwoPossibleMatchesForPatientTwo = linkChangeEvent.getMdmLinks()
			.stream()
			.filter(l -> l.getSourceId().equals(patient2.getIdElement().toVersionless().getValueAsString()) && l.getMatchResult() == MdmMatchResultEnum.POSSIBLE_MATCH)
			.count();
		assertThat(expectTwoPossibleMatchesForPatientTwo).isEqualTo(2);

		long expectOnePossibleDuplicate = linkChangeEvent.getMdmLinks()
			.stream()
			.filter(l -> l.getMatchResult() == MdmMatchResultEnum.POSSIBLE_DUPLICATE)
			.count();
		assertThat(expectOnePossibleDuplicate).isEqualTo(1);

		List<MdmLinkJson> mdmLinkEvent = linkChangeEvent.getMdmLinks();
		assertThat(mdmLinkEvent).hasSize(3);
	}

	@Test
	public void testCreateLinkChangeEvent() throws InterruptedException {
		Practitioner pr = buildPractitionerWithNameAndId("Young", "AC-DC");
		MdmHelperR4.OutcomeAndLogMessageWrapper outcome = myMdmHelper.createWithLatch(pr);

		ResourceOperationMessage resourceOperationMessage = outcome.getResourceOperationMessage();
		assertThat(resourceOperationMessage).isNotNull();
		assertThat(resourceOperationMessage.getId()).isEqualTo(pr.getIdElement().toUnqualifiedVersionless().getValue());

		MdmLink link = getLinkByTargetId(pr);

		MdmLinkEvent linkChangeEvent = outcome.getMdmLinkEvent();
		assertThat(linkChangeEvent).isNotNull();

		assertThat(linkChangeEvent.getMdmLinks()).hasSize(1);
		MdmLinkJson l = linkChangeEvent.getMdmLinks().get(0);
		assertThat(new IdDt(l.getGoldenResourceId()).getIdPartAsLong()).isEqualTo(link.getGoldenResourcePid());
		assertThat(new IdDt(l.getSourceId()).getIdPartAsLong()).isEqualTo(link.getSourcePid());
	}

	private MdmLink getLinkByTargetId(IBaseResource theResource) {
		MdmLink example = new MdmLink();
		example.setSourcePid(theResource.getIdElement().getIdPartAsLong());
		return (MdmLink) myMdmLinkDao.findAll(Example.of(example)).get(0);
	}

	@Test
	public void testUpdateLinkChangeEvent() throws InterruptedException {
		Patient patient1 = addExternalEID(buildJanePatient(), "eid-1");
		MdmHelperR4.OutcomeAndLogMessageWrapper outcome = myMdmHelper.createWithLatch(patient1);

		MdmLinkEvent linkChangeEvent = outcome.getMdmLinkEvent();
		assertThat(linkChangeEvent).isNotNull();
		assertThat(linkChangeEvent.getMdmLinks()).hasSize(1);

		MdmLinkJson link = linkChangeEvent.getMdmLinks().get(0);
		assertThat(link.getSourceId()).isEqualTo(patient1.getIdElement().toVersionless().getValueAsString());
		assertThat(new IdDt(link.getGoldenResourceId()).getIdPartAsLong()).isEqualTo(getLinkByTargetId(patient1).getGoldenResourcePid());
		assertThat(link.getMatchResult()).isEqualTo(MdmMatchResultEnum.MATCH);
	}

}
