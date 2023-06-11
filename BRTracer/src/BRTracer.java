import java.io.File;
import property.Property;

public class BRTracer {
	public static void main(String[] args) {
		float alpha = 0.3f;
		String datasetPath = args[0];;
		String bugFilePath = datasetPath+"BugRepository.xml";
		String sourceCodeDir = datasetPath+"src/";
		File dir = new File("tmp");
		dir.mkdir();
		Property.createInstance(bugFilePath, sourceCodeDir, dir
				.getAbsolutePath(), alpha, "output.txt", "aspectj", sourceCodeDir.length());
		new Core().process();
	}
}