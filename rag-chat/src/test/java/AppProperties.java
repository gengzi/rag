import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "react")
public class AppProperties {

    private String system_prompt;
    private String next_step_prompt;

    public String getSystem_prompt() {
        return system_prompt;
    }

    public void setSystem_prompt(String system_prompt) {
        this.system_prompt = system_prompt;
    }

    public String getNext_step_prompt() {
        return next_step_prompt;
    }

    public void setNext_step_prompt(String next_step_prompt) {
        this.next_step_prompt = next_step_prompt;
    }
}