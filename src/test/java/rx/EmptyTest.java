package rx;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EmptyTest {

	@Test
	public void testEmptyReturnsSameObjectRepeatedly() {
		assertTrue(Observable.empty() == Observable.empty());
	}

}
