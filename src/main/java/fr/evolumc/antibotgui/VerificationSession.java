package fr.evolumc.antibotgui;

public class VerificationSession {

    public enum Step {
        CAPTCHA,
        SERVER_NAME,
        PSEUDO,
        DONE
    }

    private Step step = Step.CAPTCHA;
    private final String captchaCode;
    private int attempts = 0;

    public VerificationSession(String captchaCode) {
        this.captchaCode = captchaCode;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        attempts++;
    }
}
