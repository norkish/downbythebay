package linguistic.syntactic;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.util.CoreMap;
import utils.Pair;

public class StanfordNlpInterface {

	private static StanfordCoreNLP pipeline;
	private static final String INPUT_TYPE = "annotators";
	private static final String ANNOTATORS = "tokenize, ssplit, pos";
	//    private final String ANNOTATORS = "tokenize, ssplit, pos, lemma, ner, parse, dcoref";
	//private static final MaxentTagger tagger = new MaxentTagger(U.rootPath + "lib/stanford-parser/3.6.0/libexec/models/wsj-0-18-bidirectional-nodistsim.tagger");
	//private final MaxentTagger tagger = new MaxentTagger(U.rootPath + "local-data/models/wordsToPos-tagger/english-left3words/english-bidirectional-distsim.tagger");
	private static DocumentPreprocessor documentPreprocessor;
	private static final TokenizerFactory<CoreLabel> ptbTokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "untokenizable=noneKeep");
	AbstractSequenceClassifier<CoreLabel> classifier = null;


	public StanfordNlpInterface() {
		this.setupPipeline();
	}

	private void setupPipeline() {
		this.buildPipeline();
	}

	private void buildPipeline() {
		Properties props = new Properties();
		props.put(INPUT_TYPE, ANNOTATORS);
		this.pipeline = new StanfordCoreNLP(props);
	}

	public List<CoreMap> parseTextToCoreMaps(String rawText) {
		// create an empty Annotation just with the given text
		Annotation document = new Annotation(rawText);

		// runOnWords all Annotators on this text
		pipeline.annotate(document);

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
		List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

		return sentences;
	}

	public List<Pair<String,Pos>> parseCoreMapsToPairs(CoreMap sentence) {
		List<Pair<String,Pos>> taggedWords = new ArrayList<>();
		for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
			String spelling = token.get(CoreAnnotations.TextAnnotation.class);
			Pos pos;
			try {
				pos = Pos.valueOf(token.get(CoreAnnotations.PartOfSpeechAnnotation.class));
			}
			catch (IllegalArgumentException e) {
				pos = null;
			}
			taggedWords.add(new Pair<>(spelling, pos));
		}
		return taggedWords;
	}

}
