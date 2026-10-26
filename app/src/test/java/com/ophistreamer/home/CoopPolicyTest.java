package com.ophistreamer.home;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoopPolicyTest {
    @Test public void acceptsSameRunningGame() { assertTrue(CoopPolicy.canJoin(42, null, 42, null)); }
    @Test public void acceptsUuidOnlyApps() { assertTrue(CoopPolicy.canJoin(0, "abc", 0, "abc")); }
    @Test public void rejectsStoppedGame() { assertFalse(CoopPolicy.canJoin(0, null, 42, null)); }
    @Test public void missingUuidsNeverMakeDifferentGamesMatch() { assertFalse(CoopPolicy.canJoin(9, null, 42, null)); }
    @Test public void emptyUuidsNeverMatch() { assertFalse(CoopPolicy.canJoin(0, "", 0, "")); }
    @Test public void rejectsChangedHostGame() { assertFalse(CoopPolicy.canJoin(9, "new", 42, "old")); }
}
