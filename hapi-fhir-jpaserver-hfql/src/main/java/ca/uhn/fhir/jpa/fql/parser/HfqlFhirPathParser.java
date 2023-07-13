/*-
 * #%L
 * HAPI FHIR JPA Server - HFQL Driver
 * %%
 * Copyright (C) 2014 - 2023 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.jpa.fql.parser;

import ca.uhn.fhir.context.*;
import ca.uhn.fhir.jpa.fql.executor.HfqlDataTypeEnum;
import org.apache.commons.text.WordUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public class HfqlFhirPathParser {

	private static final Map<String, HfqlDataTypeEnum> FHIR_DATATYPE_TO_FQL_DATATYPE;

	static {
		Map<String, HfqlDataTypeEnum> fhirDatatypeToFqlDatatype = new HashMap<>();
		fhirDatatypeToFqlDatatype.put("base64Binary", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("boolean", HfqlDataTypeEnum.BOOLEAN);
		fhirDatatypeToFqlDatatype.put("canonical", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("code", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("date", HfqlDataTypeEnum.DATE);
		fhirDatatypeToFqlDatatype.put("dateTime", HfqlDataTypeEnum.TIMESTAMP);
		fhirDatatypeToFqlDatatype.put("decimal", HfqlDataTypeEnum.DECIMAL);
		fhirDatatypeToFqlDatatype.put("id", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("instant", HfqlDataTypeEnum.TIMESTAMP);
		fhirDatatypeToFqlDatatype.put("integer", HfqlDataTypeEnum.INTEGER);
		fhirDatatypeToFqlDatatype.put("integer64", HfqlDataTypeEnum.LONGINT);
		fhirDatatypeToFqlDatatype.put("markdown", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("oid", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("positiveInt", HfqlDataTypeEnum.INTEGER);
		fhirDatatypeToFqlDatatype.put("string", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("time", HfqlDataTypeEnum.TIME);
		fhirDatatypeToFqlDatatype.put("unsignedInt", HfqlDataTypeEnum.INTEGER);
		fhirDatatypeToFqlDatatype.put("uri", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("url", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("uuid", HfqlDataTypeEnum.STRING);
		fhirDatatypeToFqlDatatype.put("xhtml", HfqlDataTypeEnum.STRING);
		FHIR_DATATYPE_TO_FQL_DATATYPE = Collections.unmodifiableMap(fhirDatatypeToFqlDatatype);
	}

	private final FhirContext myFhirContext;

	/**
	 * Constructor
	 */
	public HfqlFhirPathParser(FhirContext theFhirContext) {
		myFhirContext = theFhirContext;
	}

	/**
	 * Given a FHIRPath expression (and a resource type that it applies to), this
	 * class tries to determine the {@link HfqlDataTypeEnum HFQL Data Type} that the
	 * values will be when the expression is resolved. This is not nearly foolproof,
	 * so it is a best effort determination. If the type is ambiguous or can't be determined,
	 * this method will return {@link HfqlDataTypeEnum#STRING}.
	 */
	public HfqlDataTypeEnum determineDatatypeForPath(String theResourceType, String theFhirPath) {

		BaseRuntimeElementCompositeDefinition<?> currentElementDefinition =
				myFhirContext.getResourceDefinition(theResourceType);
		RuntimePrimitiveDatatypeDefinition leafDefinition = null;

		HfqlLexer lexer = new HfqlLexer(theFhirPath);
		boolean firstToken = true;
		while (lexer.hasNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)) {
			String nextToken = lexer.getNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)
					.getToken();

			// If the first token is the resource type, we can ignore that
			if (firstToken) {
				firstToken = false;
				if (nextToken.equals(theResourceType)) {
					continue;
				}
			}

			if (".".equals(nextToken)) {
				continue;
			}

			/*
			 * If there's a round bracket than this is a function name and not an
			 * element name. In this case we'll just move on to the next element.
			 * We're making the naive assumption here that the function is a filtering
			 * function such as in "Patient.identifier.where(system='http://foo').value"
			 * so that we can just skip the filter function and continue to navigate
			 * the element names as though the filter wasn't there. This is probably
			 * not going to hold true always, but it should be good enough for our
			 * basic type guessing.
			 */
			if (nextToken.contains("(")) {
				continue;
			}

			/*
			 * If the element has an offset operator (e.g. "name[3]") then
			 * ignore it since we only care about the elemt name part.
			 */
			int leftSquareBracketIndex = nextToken.indexOf('[');
			if (leftSquareBracketIndex != -1 && nextToken.endsWith("]")) {
				nextToken = nextToken.substring(0, leftSquareBracketIndex);
			}

			BaseRuntimeChildDefinition childDefForNode = currentElementDefinition.getChildByName(nextToken);
			if (childDefForNode == null) {
				childDefForNode = currentElementDefinition.getChildByName(nextToken + "[x]");
				if (childDefForNode != null) {
					if (lexer.peekNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)
							.getToken()
							.equals(".")) {
						lexer.consumeNextToken();
					}
					if (lexer.hasNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)) {
						String token = lexer.getNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)
								.getToken();
						if (token.startsWith("ofType(") && token.endsWith(")")) {
							String type = token.substring(7, token.length() - 1);
							nextToken = nextToken + WordUtils.capitalize(type);
						}
					}
				}
			}

			if (childDefForNode != null) {
				if (childDefForNode.getValidChildNames().contains(nextToken)) {
					BaseRuntimeElementDefinition<?> elementDefForNode = childDefForNode.getChildByName(nextToken);
					if (elementDefForNode != null) {
						if (elementDefForNode instanceof BaseRuntimeElementCompositeDefinition) {
							currentElementDefinition = (BaseRuntimeElementCompositeDefinition<?>) elementDefForNode;
							continue;
						} else if (elementDefForNode instanceof RuntimePrimitiveDatatypeDefinition) {
							leafDefinition = (RuntimePrimitiveDatatypeDefinition) elementDefForNode;
							break;
						}
					}
				}
			}

			break;
		}

		if (leafDefinition != null) {
			String finalToken = getNextFhirPathPartTokenOrNull(lexer);
			if ("toInteger()".equals(finalToken)) {
				return HfqlDataTypeEnum.INTEGER;
			}

			String typeName = leafDefinition.getName();
			return getHfqlDataTypeForFhirType(typeName);
		}

		return null;
	}

	static HfqlDataTypeEnum getHfqlDataTypeForFhirType(String theTypeName) {
		return FHIR_DATATYPE_TO_FQL_DATATYPE.get(theTypeName);
	}

	@Nullable
	private static String getNextFhirPathPartTokenOrNull(HfqlLexer lexer) {
		String finalToken = null;
		if (lexer.hasNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)) {
			finalToken = lexer.getNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)
					.getToken();
		}

		if (".".equals(finalToken)) {
			if (lexer.hasNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)) {
				finalToken = lexer.getNextToken(HfqlLexerOptions.FHIRPATH_EXPRESSION_PART)
						.getToken();
			}
		}

		return finalToken;
	}
}
