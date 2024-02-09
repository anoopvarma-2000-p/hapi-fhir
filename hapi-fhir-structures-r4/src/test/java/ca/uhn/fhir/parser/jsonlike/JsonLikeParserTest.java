package ca.uhn.fhir.parser.jsonlike;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IJsonLikeParser;
import ca.uhn.fhir.parser.json.BaseJsonLikeArray;
import ca.uhn.fhir.parser.json.BaseJsonLikeObject;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue;
import ca.uhn.fhir.parser.json.BaseJsonLikeWriter;
import ca.uhn.fhir.parser.json.JsonLikeStructure;
import ca.uhn.fhir.parser.json.jackson.JacksonStructure;
import ca.uhn.fhir.parser.view.ExtPatient;
import ca.uhn.fhir.util.AttachmentUtil;
import ca.uhn.fhir.util.ParametersUtil;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.instance.model.api.IBaseParameters;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.ICompositeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonLikeParserTest {
	private static FhirContext ourCtx = FhirContext.forR4();
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(JsonLikeParserTest.class);

	/**
	 * Test for JSON Parser with user-supplied JSON-like structure (use default GSON)
	 */
	@Test
	public void testJsonLikeParseAndEncodeResourceFromXmlToJson() throws Exception {
		String content = IOUtils.toString(JsonLikeParserTest.class.getResourceAsStream("/extension-on-line.txt"));
		
		IBaseResource parsed = ourCtx.newJsonParser().parseResource(content);

		String encoded = ourCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(parsed);
		ourLog.info(encoded);
		
		JsonLikeStructure jsonLikeStructure = new JacksonStructure();
		jsonLikeStructure.load(new StringReader(encoded));
		
		IJsonLikeParser jsonLikeparser = (IJsonLikeParser)ourCtx.newJsonParser();
		
		IBaseResource resource = jsonLikeparser.parseResource(jsonLikeStructure);
		assertThat(resource.getClass().getName()).as("reparsed resource classes not equal").isEqualTo(parsed.getClass().getName());
	}

	@Test
	public void testJacksonStructureCanLoadLoincTerminogy() throws IOException {
		// given
		IBaseParameters inputParametersForLoinc = getUploadTerminologyCommandInputParametersForLoinc();
		String s = ourCtx.newJsonParser().encodeResourceToString(inputParametersForLoinc);
		StringReader stringReader = new StringReader(s);

		// when
		JsonLikeStructure jsonLikeStructure = new JacksonStructure();
		jsonLikeStructure.load(stringReader);

		// then
		assertThat(jsonLikeStructure.getRootObject()).isNotNull();

	}

	/**
	 * Test JSON-Like writer using custom stream writer
	 * 
	 */
	@Test
	public void testJsonLikeParseWithCustomJSONStreamWriter() throws Exception {
		String refVal = "http://my.org/FooBar";

		Patient fhirPat = new Patient();
		fhirPat.addExtension().setUrl("x1").setValue(new Reference(refVal));

		IJsonLikeParser jsonLikeParser = (IJsonLikeParser)ourCtx.newJsonParser();
		JsonLikeMapWriter jsonLikeWriter = new JsonLikeMapWriter();

		jsonLikeParser.encodeResourceToJsonLikeWriter(fhirPat, jsonLikeWriter);
		Map<String,Object> jsonLikeMap = jsonLikeWriter.getResultMap();
		
		System.out.println("encoded map: " + jsonLikeMap.toString());

		assertThat(jsonLikeMap.get("resourceType")).as("Encoded resource missing 'resourceType' element").isNotNull();
		assertThat("Patient").as("Expecting 'resourceType'='Patient'; found '" + jsonLikeMap.get("resourceType") + "'").isEqualTo(jsonLikeMap.get("resourceType"));

		assertThat(jsonLikeMap.get("extension")).as("Encoded resource missing 'extension' element").isNotNull();
		assertThat((jsonLikeMap.get("extension") instanceof List)).as("'extension' element is not a List").isTrue();
		
		List<Object> extensions = (List<Object>)jsonLikeMap.get("extension");
		assertThat(extensions.size()).as("'extnesion' array has more than one entry").isEqualTo(1);
		assertThat((extensions.get(0) instanceof Map)).as("'extension' array entry is not a Map").isTrue();
		
		Map<String, Object> extension = (Map<String,Object>)extensions.get(0);
		assertThat(extension.get("url")).as("'extension' entry missing 'url' member").isNotNull();
		assertThat((extension.get("url") instanceof String)).as("'extension' entry 'url' member is not a String").isTrue();
		assertThat(extension.get("url")).as("Expecting '/extension[]/url' = 'x1'; found '" + extension.get("url") + "'").isEqualTo("x1");
	
	}

	/**
	 * Repeat the "View" tests with custom JSON-Like structure
	 */
	@Test
	public void testViewJson() throws Exception {

		ExtPatient src = new ExtPatient();
		src.addIdentifier().setSystem("urn:sys").setValue("id1");
		src.addIdentifier().setSystem("urn:sys").setValue("id2");
		src.getExt().setValue(100);
		src.getModExt().setValue(200);

		IJsonLikeParser jsonLikeParser = (IJsonLikeParser)ourCtx.newJsonParser();
		JsonLikeMapWriter jsonLikeWriter = new JsonLikeMapWriter();
		jsonLikeParser.encodeResourceToJsonLikeWriter(src, jsonLikeWriter);
		Map<String,Object> jsonLikeMap = jsonLikeWriter.getResultMap();
		

		ourLog.info("encoded: "+jsonLikeMap);

		JsonLikeStructure jsonStructure = new JsonLikeMapStructure(jsonLikeMap);
		IJsonLikeParser parser = (IJsonLikeParser)ourCtx.newJsonParser();
		Patient nonExt = parser.parseResource(Patient.class, jsonStructure);

		assertThat(nonExt.getClass()).isEqualTo(Patient.class);
		assertThat(nonExt.getIdentifier().get(0).getSystem()).isEqualTo("urn:sys");
		assertThat(nonExt.getIdentifier().get(0).getValue()).isEqualTo("id1");
		assertThat(nonExt.getIdentifier().get(1).getSystem()).isEqualTo("urn:sys");
		assertThat(nonExt.getIdentifier().get(1).getValue()).isEqualTo("id2");

		List<Extension> ext = nonExt.getExtensionsByUrl("urn:ext");
		assertThat(ext).hasSize(1);
		assertThat(ext.get(0).getUrl()).isEqualTo("urn:ext");
		assertThat(ext.get(0).getValueAsPrimitive().getClass()).isEqualTo(IntegerType.class);
		assertThat(ext.get(0).getValueAsPrimitive().getValueAsString()).isEqualTo("100");

		List<Extension> modExt = nonExt.getExtensionsByUrl("urn:modExt");
		assertThat(modExt).hasSize(1);
		assertThat(modExt.get(0).getUrl()).isEqualTo("urn:modExt");
		assertThat(modExt.get(0).getValueAsPrimitive().getClass()).isEqualTo(IntegerType.class);
		assertThat(modExt.get(0).getValueAsPrimitive().getValueAsString()).isEqualTo("200");

		ExtPatient va = ourCtx.newViewGenerator().newView(nonExt, ExtPatient.class);
		assertThat(va.getIdentifier().get(0).getSystem()).isEqualTo("urn:sys");
		assertThat(va.getIdentifier().get(0).getValue()).isEqualTo("id1");
		assertThat(va.getIdentifier().get(1).getSystem()).isEqualTo("urn:sys");
		assertThat(va.getIdentifier().get(1).getValue()).isEqualTo("id2");
		assertThat(va.getExt().getValue().intValue()).isEqualTo(100);
		assertThat(va.getModExt().getValue().intValue()).isEqualTo(200);

		assertThat(va.getExtension()).isEmpty();
	}

	private IBaseParameters getUploadTerminologyCommandInputParametersForLoinc() throws IOException {
		IBaseParameters inputParameters = ParametersUtil.newInstance(ourCtx);
		ParametersUtil.addParameterToParametersUri(
			ourCtx, inputParameters, "system", "http://loinc.org");

		try(InputStream inputStream = JsonLikeParserTest.class.getResourceAsStream("/Loinc_2.72.zip")) {

			ICompositeType attachment = AttachmentUtil.newInstance(ourCtx);
			AttachmentUtil.setContentType(ourCtx, attachment, "application/zip");
			AttachmentUtil.setUrl(ourCtx, attachment, "Loinc_2.72.zip");

			AttachmentUtil.setData(ourCtx, attachment, IOUtils.toByteArray(inputStream));

			ParametersUtil.addParameterToParameters(
				ourCtx, inputParameters, "file", attachment);

		}

		return inputParameters;

	}

	@AfterAll
	public static void afterClassClearContext() {
		TestUtil.randomizeLocaleAndTimezone();
	}

	public static class JsonLikeMapWriter extends BaseJsonLikeWriter {

		private Map<String,Object> target;
		
		private static class Block {
			private BlockType type;
			private String name;
			private Map<String,Object> object;
			private List<Object> array;
			public Block(BlockType type) {
				this.type = type;
			}
			public BlockType getType() {
				return type;
			}
			public String getName() {
				return name;
			}
			public void setName(String currentName) {
				this.name = currentName;
			}
			public Map<String, Object> getObject() {
				return object;
			}
			public void setObject(Map<String, Object> currentObject) {
				this.object = currentObject;
			}
			public List<Object> getArray() {
				return array;
			}
			public void setArray(List<Object> currentArray) {
				this.array = currentArray;
			}
		}
		private enum BlockType {
			NONE, OBJECT, ARRAY
		}
		private Block currentBlock = new Block(BlockType.NONE);
		private Stack<Block> blockStack = new Stack<>();

		public JsonLikeMapWriter () {
			super();
		}
		
		public Map<String,Object> getResultMap() {
			return target;
		}
		public void setResultMap(Map<String,Object> target) {
			this.target = target;
		}

		@Override
		public BaseJsonLikeWriter init() {
			if (target != null) {
				target.clear();
			}
			currentBlock = new Block(BlockType.NONE);
			blockStack.clear();
			return this;
		}

		@Override
		public BaseJsonLikeWriter flush() throws IOException {
			if (currentBlock.getType() != BlockType.NONE) {
				throw new IOException("JsonLikeStreamWriter.flush() called but JSON document is not finished");
			}
			return this;
		}

		@Override
		public void close() {
			// nothing to do
		}

		@Override
		public BaseJsonLikeWriter beginObject() throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			Map<String,Object> newObject;
			if (currentBlock.getType() == BlockType.NONE) {
				if (null == target) {
					// for this test, we don't care about ordering of map elements
					// target = new EntryOrderedMap<String,Object>();
					target = new HashMap<>();
				}
				newObject = target;
			} else {
				// for this test, we don't care about ordering of map elements
				// newObject = new EntryOrderedMap<String,Object>();
				newObject = new HashMap<>();
			}
			blockStack.push(currentBlock);
			currentBlock = new Block(BlockType.OBJECT);
			currentBlock.setObject(newObject);
			return this;
		}

		@Override
		public BaseJsonLikeWriter beginObject(String name) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			blockStack.push(currentBlock);
			currentBlock = new Block(BlockType.OBJECT);
			currentBlock.setName(name);
			// for this test, we don't care about ordering of map elements
			// currentBlock.setObject(new EntryOrderedMap<String,Object>());
			currentBlock.setObject(new HashMap<>());
			return this;
		}

		@Override
		public BaseJsonLikeWriter beginArray(String name) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			blockStack.push(currentBlock);
			currentBlock = new Block(BlockType.ARRAY);
			currentBlock.setName(name);
			currentBlock.setArray(new ArrayList<>());
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(value);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(BigInteger value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(value);
			return this;
		}
		
		@Override
		public BaseJsonLikeWriter write(BigDecimal value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(value);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(long value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(Long.valueOf(value));
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(double value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(Double.valueOf(value));
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(Boolean value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(value);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(boolean value) throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(Boolean.valueOf(value));
			return this;
		}

		@Override
		public BaseJsonLikeWriter writeNull() throws IOException {
			if (currentBlock.getType() == BlockType.OBJECT) {
				throw new IOException("Unnamed JSON elements can only be created in JSON arrays");
			}
			currentBlock.getArray().add(null);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String name, String value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, value);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String name, BigInteger value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, value);
			return this;
		}
		@Override
		public BaseJsonLikeWriter write(String name, BigDecimal value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, value);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String name, long value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, Long.valueOf(value));
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String name, double value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, Double.valueOf(value));
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String name, Boolean value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, value);
			return this;
		}

		@Override
		public BaseJsonLikeWriter write(String name, boolean value) throws IOException {
			if (currentBlock.getType() == BlockType.ARRAY) {
				throw new IOException("Named JSON elements can only be created in JSON objects");
			}
			currentBlock.getObject().put(name, Boolean.valueOf(value));
			return this;
		}

		@Override
		public BaseJsonLikeWriter endObject() throws IOException {
			if (currentBlock.getType() == BlockType.NONE) {
				ourLog.error("JsonLikeStreamWriter.endObject(); called with no active JSON document");
			} else {
				if (currentBlock.getType() != BlockType.OBJECT) {
					ourLog.error("JsonLikeStreamWriter.endObject(); called outside a JSON object. (Use endArray() instead?)");
				}
				endBlock();
			}
			return this;
		}

		@Override
		public BaseJsonLikeWriter endArray() {
			if (currentBlock.getType() == BlockType.NONE) {
				ourLog.error("JsonLikeStreamWriter.endArray(); called with no active JSON document");
			} else {
				if (currentBlock.getType() != BlockType.ARRAY) {
					ourLog.error("JsonLikeStreamWriter.endArray(); called outside a JSON array. (Use endObject() instead?)");
				}
				endBlock();
			}
			return this;
		}

		@Override
		public BaseJsonLikeWriter endBlock() {
			if (currentBlock.getType() == BlockType.NONE) {
				ourLog.error("JsonLikeStreamWriter.endBlock(); called with no active JSON document");
			} else {
				Object toPut;
				if (currentBlock.getType() == BlockType.ARRAY) {
					toPut = currentBlock.getArray();
				} else {
					toPut = currentBlock.getObject();
				}
				Block parentBlock = blockStack.pop(); 
				if (parentBlock.getType() == BlockType.OBJECT) {
					parentBlock.getObject().put(currentBlock.getName(), toPut);
				} else 
				if (parentBlock.getType() == BlockType.ARRAY) {
					parentBlock.getArray().add(toPut);
				} 
				currentBlock = parentBlock;
			}
			return this;
		}

	}
	
	public static class JsonLikeMapStructure implements JsonLikeStructure {

		private Map<String,Object> nativeObject;
		private BaseJsonLikeObject jsonLikeObject = null;
		private JsonLikeMapWriter jsonLikeWriter = null;
		
		public JsonLikeMapStructure() {
			super();
		}
		
		public JsonLikeMapStructure (Map<String,Object> json) {
			super();
			setNativeObject(json);
		}
		
		public void setNativeObject (Map<String,Object> json) {
			this.nativeObject = json;
		}

		@Override
		public JsonLikeStructure getInstance() {
			return new JsonLikeMapStructure();
		}

		@Override
		public BaseJsonLikeWriter getJsonLikeWriter (Writer ignored) {
			return getJsonLikeWriter();
		}
		
		@Override
		public BaseJsonLikeWriter getJsonLikeWriter () {
			if (null == jsonLikeWriter) {
				jsonLikeWriter = new JsonLikeMapWriter();
			}
			return jsonLikeWriter;
		}

		@Override
		public void load(Reader reader) throws DataFormatException {
			this.load(reader, true);
		}

		@Override
		public void load(Reader theReader, boolean allowArray) throws DataFormatException {
			throw new DataFormatException("JSON structure loading is not supported for native Java Map structures");
		}

		@Override
		public BaseJsonLikeObject getRootObject() {
			if (null == jsonLikeObject) {
				jsonLikeObject = new JsonMapObject(nativeObject);
			}
			return jsonLikeObject;
		}

		private class JsonMapObject extends BaseJsonLikeObject {
			private Map<String,Object> nativeObject;
			private Map<String, BaseJsonLikeValue> jsonLikeMap = new LinkedHashMap<>();
			
			public JsonMapObject (Map<String,Object> json) {
				this.nativeObject = json;
			}

			@Override
			public Object getValue() {
				return nativeObject;
			}

			@Override
			public Iterator<String> keyIterator() {
				return nativeObject.keySet().iterator();
			}

			@Override
			public BaseJsonLikeValue get(String key) {
				BaseJsonLikeValue result = null;
				if (jsonLikeMap.containsKey(key)) {
					result = jsonLikeMap.get(key); 
				} else {
					Object child = nativeObject.get(key);
					if (child != null) {
						result = new JsonMapValue(child);
					}
					jsonLikeMap.put(key, result);
				}
				return result;
			}
		}
		
		private class JsonMapArray extends BaseJsonLikeArray {
			private List<Object> nativeArray;
			private Map<Integer, BaseJsonLikeValue> jsonLikeMap = new LinkedHashMap<>();
			
			public JsonMapArray (List<Object> json) {
				this.nativeArray = json;
			}

			@Override
			public Object getValue() {
				return nativeArray;
			}

			@Override
			public int size() {
				return nativeArray.size();
			}

			@Override
			public BaseJsonLikeValue get(int index) {
				Integer key = index;
				BaseJsonLikeValue result = null;
				if (jsonLikeMap.containsKey(key)) {
					result = jsonLikeMap.get(key); 
				} else {
					Object child = nativeArray.get(index);
					if (child != null) {
						result = new JsonMapValue(child);
					}
					jsonLikeMap.put(key, result);
				}
				return result;
			}
		}
		
		private class JsonMapValue extends BaseJsonLikeValue {
			private Object nativeValue;
			private BaseJsonLikeObject jsonLikeObject = null;
			private BaseJsonLikeArray jsonLikeArray = null;
			
			public JsonMapValue (Object json) {
				this.nativeValue = json;
			}

			@Override
			public Object getValue() {
				return nativeValue;
			}
			
			@Override
			public ValueType getJsonType() {
				if (isNull()) {
					return ValueType.NULL;
				}
				if (isObject()) {
					return ValueType.OBJECT;
				}
				if (isArray()) {
					return ValueType.ARRAY;
				}
				return ValueType.SCALAR;
			}
			
			@Override
			public ScalarType getDataType() {
				if (isString()) {
					return ScalarType.STRING;
				}
				if (isNumber()) {
					return ScalarType.NUMBER;
				}
				if (isBoolean()) {
					return ScalarType.BOOLEAN;
				}
				return null;
			}

			@SuppressWarnings("unchecked")
			@Override
			public BaseJsonLikeArray getAsArray() {
				if (nativeValue != null && isArray()) {
					if (null == jsonLikeArray) {
						jsonLikeArray = new JsonMapArray((List<Object>)nativeValue);
					}
				}
				return jsonLikeArray;
			}

			@SuppressWarnings("unchecked")
			@Override
			public BaseJsonLikeObject getAsObject() {
				if (nativeValue != null && isObject()) {
					if (null == jsonLikeObject) {
						jsonLikeObject = new JsonMapObject((Map<String,Object>)nativeValue);
					}
				}
				return jsonLikeObject;
			}

			@Override
			public String getAsString() {
				String result = null;
				if (nativeValue != null) {
					result = nativeValue.toString();
				}
				return result;
			}

			@Override
			public boolean getAsBoolean() {
				if (nativeValue != null && isBoolean()) {
					return (Boolean) nativeValue;
				}
				return super.getAsBoolean();
			}

			@Override
			public boolean isObject () {
				return (nativeValue != null)
					&& ( (nativeValue instanceof Map) || Map.class.isAssignableFrom(nativeValue.getClass()) );
			}

			@Override
			public boolean isArray () {
				return (nativeValue != null)
					&& ( (nativeValue instanceof List) || List.class.isAssignableFrom(nativeValue.getClass()));
			}

			@Override
			public boolean isString () {
				return (nativeValue != null)
					&& ( (nativeValue instanceof String) || String.class.isAssignableFrom(nativeValue.getClass()));
			}

			@Override
			public boolean isNumber () {
				return (nativeValue != null)
					&& ( (nativeValue instanceof Number) || Number.class.isAssignableFrom(nativeValue.getClass()) );
			}

			public boolean isBoolean () {
				return (nativeValue != null)
					&& ( (nativeValue instanceof Boolean) || Boolean.class.isAssignableFrom(nativeValue.getClass()) );
			}
			
			@Override
			public boolean isNull () {
				return (null == nativeValue);
			}
		}
	}
}
