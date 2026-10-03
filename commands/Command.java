package JarvisAI.commands;

public interface Command {

    boolean matches(String input);

    String execute(String input);

}