package com.ophistreamer.home;

/** Pure policy so the host-game race can be tested without a device. */
public final class CoopPolicy {
    private CoopPolicy() { }

    public static boolean canJoin(int runningId, String runningUuid, int requestedId, String requestedUuid) {
        boolean byId = runningId > 0 && requestedId > 0 && runningId == requestedId;
        boolean byUuid = runningUuid != null && !runningUuid.isEmpty()
                && requestedUuid != null && !requestedUuid.isEmpty() && runningUuid.equals(requestedUuid);
        return byId || byUuid;
    }
}
