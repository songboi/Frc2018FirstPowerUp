package team492.diagnostics.tests;

import team492.PixyVision;
import team492.diagnostics.DiagnosticsTest;

public class PixyVisionTaskTerminatedTest implements DiagnosticsTest {

    private final PixyVision pixyVision;

    private boolean hasTaskEverTerminated = false;

    public PixyVisionTaskTerminatedTest(PixyVision pixyVision) {
        this.pixyVision = pixyVision;
    }

    @Override
    public void test() {
        hasTaskEverTerminated |= pixyVision.isTaskTerminatedAbnormally();
    }

    @Override
    public TestResult getResult() {
        return new TestResult(hasTaskEverTerminated,
                "Pixy vision background task terminated unexpectedly - " +
                        "check console & Pixy camera wiring");
    }
}