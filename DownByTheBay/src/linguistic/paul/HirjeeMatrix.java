package linguistic.paul;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import linguistic.phonetic.ConsonantPhoneme;
import linguistic.phonetic.VowelPhoneme;
import linguistic.phonetic.syllabic.Syllable;
import utils.Pair;

public class HirjeeMatrix {

	private static double[][] matrix = load();
	private static final String hirjeeFilePath = "data/hirjeeMatrix.txt";
	private static final int UNMATCHED_CODA_CONSONANT_AT_BEGINNING = 38;
	private static final int UNMATCHED_CODA_CONSONANT_AT_END = 39;

	public static double[][] load() {
		if (matrix == null) {

			BufferedReader bf;
			try {
				bf = new BufferedReader(new FileReader(hirjeeFilePath));

				String line = bf.readLine();
				String[] lineSplit = line.split("\t");
				int matrixWidth = lineSplit.length - 1;
				double value;
				matrix = new double[matrixWidth][matrixWidth];
				// System.out.println(Arrays.toString(lineSplit));
				// System.out.println(matrix.length);
				// System.out.println(matrix[1].length);

				int lineNum = 0;
				while ((line = bf.readLine()) != null) {
					lineSplit = line.split("\t");
					for (int i = lineNum; i < matrixWidth; i++) {
						if (i + 1 >= lineSplit.length || lineSplit[i + 1].length() == 0) {
							value = -50.0;
						} else {
							value = Double.parseDouble(lineSplit[i + 1]) * 10.;
						}

						matrix[lineNum][i] = value;
						matrix[i][lineNum] = value;
					}
					lineNum++;
				}

				bf.close();
			} catch (NumberFormatException | IOException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}
		}

		return matrix;
	}

	public static void main(String[] args) throws IOException {
		double[][] matrix = HirjeeMatrix.load();
		Map<String, Pair<Integer, PhoneCategory>> phonesDict = Phonetecizer.loadPhonesDict();

		for (double[] array : matrix) {
			for (double num : array)
				System.out.print(num + "\t");
			System.out.println();
		}

		for (String phone : phonesDict.keySet()) {
			System.out.println("AA with " + phone + ": "
					+ matrix[phonesDict.get(phone).getFirst()][phonesDict.get("AA").getFirst()]);
		}
	}

	public static double scoreSyllables(Syllable s1, Syllable s2) {
		
		// vowel
		double vowelScore = 0.0;
		VowelPhoneme vowel1 = s1.getVowel();
		VowelPhoneme vowel2 = s2.getVowel();
		vowelScore += matrix[vowel1.phonemeEnum.ordinal()][vowel2.phonemeEnum.ordinal()];
		
		// coda
		double codaScore = 0.0;
		List<ConsonantPhoneme> coda1 = s1.getCoda();
		List<ConsonantPhoneme> coda2 = s2.getCoda();
		
		double[][] matrix = new double[coda1.size()+1][coda2.size()+1];

		for (int row = 0; row <= coda1.size(); row++) {
			matrix[row][0] = 0; // set left col to 0
		}
		for (int col = 0; col <= coda2.size(); col++) {
			matrix[0][col] = 0; // set top row to 0
		}
		
		int cons1Idx, cons2Idx;
		double diag, up, left;
		for (int row = 1; row <= coda1.size(); row++) {
			double[] prevMatrixRow = matrix[row-1];
			double[] currMatrixRow = matrix[row];
			for (int col = 1; col <= coda2.size(); col++) {
				cons1Idx = coda1.get(row-1).phonemeEnum.ordinal();
				cons2Idx = coda2.get(col-1).phonemeEnum.ordinal();
				
				diag = prevMatrixRow[col-1] + matrix[cons1Idx][cons2Idx];
				left = currMatrixRow[col-1] + (row == 0 ? UNMATCHED_CODA_CONSONANT_AT_BEGINNING : (row == coda1.size()? UNMATCHED_CODA_CONSONANT_AT_END : Double.NEGATIVE_INFINITY));
				up = prevMatrixRow[col] + (col == 0 ? UNMATCHED_CODA_CONSONANT_AT_BEGINNING : (col == coda2.size()? UNMATCHED_CODA_CONSONANT_AT_END : Double.NEGATIVE_INFINITY));

				if (diag >= up) {
					if (diag >= left) {
						currMatrixRow[col] = diag;
					} else {
						currMatrixRow[col] = left;
					}
				} else {
					if (up >= left) {
						currMatrixRow[col] = up;
					} else {
						currMatrixRow[col] = left;
					}
				}
			}
		}
		codaScore = matrix[coda1.size()][coda2.size()]/Math.max(coda1.size(), coda2.size());
		
		// stress
		double stressScore = 0.0;
		
		return vowelScore + codaScore + stressScore;
	}
	
	public static double score(int phone, int phone2) {
		return matrix[phone][phone2];
	}

}
