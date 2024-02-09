package ca.uhn.fhir.sl.cache.guava;

import ca.uhn.fhir.sl.cache.CacheFactory;
import ca.uhn.fhir.sl.cache.LoadingCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheLoaderTest {
	@Test
	void loaderReturnsNullTest() {
		LoadingCache<String, String> cache = CacheFactory.build(1000, key -> {
			return null;
		});
		assertThat(cache.get("1")).isNull();
	}
}
