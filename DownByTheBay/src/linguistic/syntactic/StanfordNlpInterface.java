package linguistic.syntactic;

import data.SyllableToken;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.DocumentPreprocessor;

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

import java.util.*;

public class StanfordNlpInterface {

	private static StanfordCoreNLP pipeline;
	private static final String INPUT_TYPE = "annotators";
	private static final String ANNOTATORS = "tokenize, ssplit, pos, lemma";//removed ", ner" from end
	//    private final String ANNOTATORS = "tokenize, ssplit, pos, lemma, ner, parse, dcoref";
	//private static final MaxentTagger tagger = new MaxentTagger(U.rootPath + "lib/stanford-parser/3.6.0/libexec/models/wsj-0-18-bidirectional-nodistsim.tagger");
	//private final MaxentTagger tagger = new MaxentTagger(U.rootPath + "local-data/models/wordsToPos-tagger/english-left3words/english-bidirectional-distsim.tagger");
	private static DocumentPreprocessor documentPreprocessor;
	private static final TokenizerFactory<CoreLabel> ptbTokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "untokenizable=noneKeep");
	AbstractSequenceClassifier<CoreLabel> classifier = null;


	public StanfordNlpInterface() {
		this.setupPipeline();
	}

	public void setupPipeline() {
		this.buildPipeline();
	}

	private void buildPipeline() {
		Properties props = new Properties();
		props.put(INPUT_TYPE, ANNOTATORS);
		this.pipeline = new StanfordCoreNLP(props);
	}

	public static List<Sentence> parseTextToSentences(String rawText) {
		//TODO: eventually preserve punctuation in Sentence object

		//make the result object
		ArrayList<Sentence> mySentences = new ArrayList<>();

		// create an empty Annotation just with the given text
		Annotation document = new Annotation(rawText);

		// runOnWords all Annotators on this text
		pipeline.annotate(document);

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
		List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

		for (CoreMap tempCoreMap : sentences) {
			Sentence tempSentence = new Sentence();
			tempSentence.setCoreMap(tempCoreMap);
			int sentenceIndex = 0;
			// traversing the filterWords in the current sentence
			// a CoreLabel is a CoreMap with additional token-specific methods
			for (CoreLabel token : tempCoreMap.get(CoreAnnotations.TokensAnnotation.class)) {
				// this is the text of the token
				String spelling = token.get(CoreAnnotations.TextAnnotation.class);

				if (spelling.length() == 1 && !Character.isLetterOrDigit(spelling.charAt(0))) {
					//it's punctuation
					Punctuation punct = new Punctuation(spelling);
					punct.setSentence(tempSentence);
					punct.setSentenceIndex(sentenceIndex);
					tempSentence.add(punct);
				}
				else {
					// this is the POS tag of the token
					String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
					// this is the NER label of the token
					String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

					try {
						Pos.valueOf(pos);
					}
					catch (IllegalArgumentException e) {
						//System.out.println("BAD POS OR NE TOKEN: " + wordsToPos + " or " + filterNe + " FOR THE WORD: " + spelling);
						//e.printStackTrace();
						spelling = "";
					}
					if (spelling.length() < 1) {
						//it's empty, do nothing
					}
//                    else if (spelling.contains("'")) {
//                        Word lastWord = tempSentence.get(tempSentence.size() - 1);
//                        lastWord.setSpelling(lastWord.getLowerSpelling() + spelling);
//                        lastWord.setPos(Pos.CONTRACTION_WORD);
//                        String[] fullStrings = ContractionManager.getExpansion(lastWord.getLowerSpelling()+spelling);
//                        List<Word> words = new ArrayList<>();
//                        if (fullStrings != null && fullStrings.length >= 1) {
//                            Word word1 = new Word(fullStrings[0]);
//                            words.add(word1);//TODO get other attributes besides spelling!
//                            if (fullStrings != null && fullStrings.length == 2) {
//                                Word word2 = new Word(fullStrings[1]);//TODO get other attributes besides spelling!
//                                words.add(word2);
//                            }
//                        }
//                        Word contraction = new ContractionWord(lastWord.getLowerSpelling()+spelling, words);
//                    }
					else {
						//Make a new word object
						Word tempWord = new Word(spelling);
						tempWord.setBase(token.get(CoreAnnotations.LemmaAnnotation.class));
						tempWord.setPos(Pos.valueOf(pos));

						//Add it to the sentence object
						tempWord.setSentence(tempSentence);
						tempWord.setSentenceIndex(sentenceIndex);
						tempSentence.add(tempWord);
					}
				}
				sentenceIndex++;
				//System.out.println("token: " + spelling + " wordsToPos: " + wordsToPos + " filterNe:" + filterNe);
			}

			mySentences.add(tempSentence);

//            // this is the parse tree of the current sentence
//            Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
//            System.out.println("parse tree:\n" + tree);
//
//            // this is the Stanford dependency graph of the current sentence
//            SemanticGraph dependencies = sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
//            System.out.println("dependency graph:\n" + dependencies);
		}

		// This is the coreference link graph
		// Each chain stores a set of mentions that link to each other,
		// along with a method for getting the most representative mention
		// Both sentence and token offsets start at 1!
//        Map<Integer, CoreNLPProtos.CorefChain> graph =
//                document.get(CorefChainAnnotation.class);
		return mySentences;
	}

}
