package ca.uhn.fhir.model.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class PagingIteratorTest {

	private PagingIterator<String> myPagingIterator;

	private List<String> getDataList(int theSize) {
		ArrayList<String> data = new ArrayList<>();
		for (int i = 0; i < theSize; i++) {
			data.add("DataString " + i);
		}
		return data;
	}

	private PagingIterator<String> createPagingIterator(int theDataSize) {
		List<String> data = getDataList(theDataSize);

		return new PagingIterator<>((thePageIndex, theBatchSize, theConsumer) -> {
			int start = (thePageIndex * theBatchSize);
			for (int i = start; i < Math.min(start + theBatchSize, theDataSize); i++) {
				theConsumer.accept(data.get(i));
			}
		});
	}

	@Test
	public void hasNext_returnsTrue_ifElementsAvailable() {
		myPagingIterator = createPagingIterator(1);

		assertThat(myPagingIterator.hasNext()).isTrue();
	}

	@Test
	public void hasNext_returnsFalse_ifNoElementsAvialable() {
		myPagingIterator = createPagingIterator(0);

		assertThat(myPagingIterator.hasNext()).isFalse();
	}

	@Test
	public void next_whenNextIsAvailable_fetches() {
		myPagingIterator = createPagingIterator(10);

		String next = myPagingIterator.next();
		assertThat(next).isNotNull();
		assertThat(next).isNotEmpty();
	}

	@Test
	public void next_fetchTest_fetchesAndReturns() {
		// 3 cases to make sure we get the edge cases
		for (int adj : new int[] { -1, 0, 1 }) {
			int size = PagingIterator.PAGE_SIZE + adj;

			myPagingIterator = createPagingIterator(size);

			// test
			int count = 0;
			while (myPagingIterator.hasNext()) {
				myPagingIterator.next();
				count++;
			}
			assertThat(count).isEqualTo(size);
		}
	}

	@Test
	public void next_throwsNoSuchElement_whenNoElements() {
		myPagingIterator = createPagingIterator(0);

		try {
			myPagingIterator.next();
			fail("");
		} catch (NoSuchElementException ex) {
			assertThat(ex.getMessage()).contains("Nothing to fetch");
		}
	}
}
