package ca.uhn.fhir.model;

import org.hl7.fhir.dstu2.model.Narrative;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class XhtmlNodeTest {

	@Test
	@Disabled
	public void testNamespaces() {
		
		Narrative type = new Narrative();
		XhtmlNode div = type.getDiv();
		div.setValue("<xhtml:div xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">hello</xhtml:div>");

		assertThat(div.getValue()).isEqualTo("<xhtml:div xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">hello</xhtml:div>");

	}
	
}
