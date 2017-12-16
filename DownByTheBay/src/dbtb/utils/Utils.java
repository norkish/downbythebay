package dbtb.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.Map.Entry;

public class Utils {
	
	private static Scanner scanner = new Scanner(System.in);
	public static void promptEnterKey(String string) {
		System.out.print(string);
		scanner.nextLine();
	}
	/*
	 * Credit to http://javatechniques.com/blog/faster-deep-copies-of-java-objects/
	 */
	public static Object deepCopy(Object orig) {
        Object obj = null;
        try {
            // Write the object out to a byte array
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(orig);
            out.flush();
            out.close();

            // Make an input stream from the byte array and read
            // a copy of the object back in.
            ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray()));
            obj = in.readObject();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        catch(ClassNotFoundException cnfe) {
            cnfe.printStackTrace();
        }
        return obj;
    }

	public static <T> String join(List<T> line, String delimiter) {
		StringBuilder str = new StringBuilder();
		boolean first = true;
		for (T object : line) {
			if(!first)
				str.append(delimiter);
			else
				first = false;
			str.append(object);
		}
		
		return str.toString();
	}

	public static String getPositionString(int i)
	{
		String posStr = "the ";
		if (i == -1)
		{
			posStr += "LAST";
		}
		else if (i == 0)
		{
			posStr += "FIRST";
		}
		else if (i == 1)
		{
			posStr += "SECOND";
		}
		else if (i == 2)
		{
			posStr += "THIRD";
		}
		else
		{
			posStr += (i+1) + "TH";
		}
		
		return posStr + " position";
	}

	public static <T extends Comparable<T>> Map<T, List<Integer>> sortByListSize(Map<T, List<Integer>> map, final boolean order)
    {
        List<Entry<T, List<Integer>>> list = new LinkedList<Entry<T, List<Integer>>>(map.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Entry<T, List<Integer>>>()
        {
            public int compare(Entry<T, List<Integer>> o1,
                    Entry<T, List<Integer>> o2)
            {
                if (order)
                {
                    return o1.getValue().size() - o2.getValue().size();
                }
                else
                {
                    return o2.getValue().size() - o1.getValue().size();

                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<T, List<Integer>> sortedMap = new LinkedHashMap<T, List<Integer>>();
        for (Entry<T, List<Integer>> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

	public static <T> void incrementValueForKey(Map<T, Double> map, T key) {
		incrementValueForKey(map, key, 1.0);
	}
	
	public static <T> void incrementValueForKey(Map<T, Double> map, T key, Double incrementAmount) {
		Double count = map.get(key);
		map.put(key, count == null ? incrementAmount : count + incrementAmount);		
	}

	public static <S, T> void incrementValueForKeys(Map<S, Map<T, Double>> map2d, S key1,
			T key2) {
		incrementValueForKeys(map2d, key1, key2, 1.0);
	}
	
	public static <S, T> void incrementValueForKeys(Map<S, Map<T, Double>> map2d, S key1,
			T key2, Double incrementAmount) {
		Map<T,Double> map1d = map2d.get(key1);
		if (map1d == null) { // never even seen prevState
			map1d = new HashMap<T,Double>();
			map1d.put(key2, incrementAmount);
			map2d.put(key1, map1d);
		} else { // seen prev state
			Double count = map1d.get(key2);
			map1d.put(key2, count == null ? 1 : count + incrementAmount);
		}		
	}

	public static <S extends Comparable<S>, T> T valueForKeyBeforeOrEqualTo(S currPos, SortedMap<S, T> tokens) {
		T currToken = null;
		for (S pos : tokens.keySet()) {
			if (pos.compareTo(currPos) > 0) {
				return currToken;
			}
			currToken = tokens.get(pos);
		}
		return currToken;
	}

	public static <S extends Comparable<S>, T> T valueForKeyBeforeOrEqualTo(Integer outerKey, S innerKey, SortedMap<Integer, SortedMap<S, T>> tokens) {
		T returnVal = null;
		
		if (tokens.containsKey(outerKey)) {
			returnVal = valueForKeyBeforeOrEqualTo(innerKey, tokens.get(outerKey));
		} 
		
		if (returnVal != null) 
			return returnVal;
		
		outerKey--;
		while (!tokens.containsKey(outerKey) && outerKey >= 0) {
			outerKey--;
		}
		
		SortedMap<S, T> innerMap = tokens.get(outerKey);
		
		if (innerMap == null) {
			return null;
		} else {
			return innerMap.get(innerMap.lastKey());
		}
	}

	public static void normalizeByFirstDimension(Map<Integer, Map<Integer, Double>> transitions) {
		for (Map<Integer, Double> row : transitions.values()) {
			normalize(row);
		}
	}
	public static void normalize(Map<Integer, Double> row) {
		//compute sum
		double sum = 0.0;
		for (Double colVal : row.values()) {
			sum += colVal;
		}
		
		//normalize
		for (Entry<Integer, Double> entry : row.entrySet()) {
			row.put(entry.getKey(), entry.getValue()/sum);
		}
	}

	public static void normalizeByMaxVal(double[][] matrix) {
		double max = 0.0;
		for (double[] row : matrix) {
			for (double colVal : row) {
				if (colVal > max) {
					max = colVal;
				}
			}
		}		

		if (max == 0)
			return;
		//normalize
		for (double[] row : matrix) {
			for (int i = 0; i < row.length; i ++) {
				row[i] /= max;
			}
		}	
	}

	public static boolean isNullorEmpty(Collection c) {
		if (c == null || c.isEmpty())
			return true;
		return false;
	}

	public static <S,T,U> U setValueForKeys(Map<S, Map<T, U>> map, S outerKey,
			T innerKey, U value) {
		Map<T, U> innerMap = map.get(outerKey);
		if (innerMap == null) {
			innerMap = new HashMap<T,U>();
			map.put(outerKey, innerMap);
		}
		
		return innerMap.put(innerKey, value);
	}
	
	public static <S,T,U,V> V setValueForKeys(Map<S, Map<T, Map<U, V>>> map,
			S outerKey, T middleKey, U innerKey, V value) {
		Map<U, V> valueForKeys = getValueForKeys(map,outerKey, middleKey);
		if (valueForKeys == null) {
			valueForKeys = new HashMap<U, V>();
			setValueForKeys(map, outerKey, middleKey, valueForKeys);
		}
		
		return valueForKeys.put(innerKey, value);
	}
	
	public static <S,T,U> U getValueForKeys(Map<S, Map<T, U>> map, S outerKey,
			T innerKey) {
		Map<T, U> innerMap = map.get(outerKey);
		if (innerMap == null) {
			return null;
		}
		
		return innerMap.get(innerKey);
	}
	
	public static <S,T,U,V> V getValueForKeys(Map<S, Map<T, Map<U, V>>> map,
			S outerKey, T middleKey, U innerKey) {
		Map<U, V> valueForKeys = getValueForKeys(map,outerKey, middleKey);
		if (valueForKeys == null) return null;
		return valueForKeys.get(innerKey);
	}
	
	private static Random rand = new Random();
	/**
	 * Assumed that p_i represents a distribution where each key k has probability value v
	 * @param p_i
	 * @return
	 */
	public static <T> T sample(Map<T, Double> p_i) {
		double total = 0.0;
		for (Double val : p_i.values()) {
			total += val;
		}
		
		double r = rand.nextDouble() * total;
		double sum = 0.0;
		
		for (Entry<T, Double> entry : p_i.entrySet()) {
			sum += entry.getValue();
			if (sum >= r) return entry.getKey();
		}
		
		throw new RuntimeException("Total was " + total + "; sum was " + sum);
	}
	public static <T,S,U> U removeKeys(Map<T, Map<S, U>> delta, T outerKey, S innerKey) {
		if (!delta.containsKey(outerKey)) return null;
		final U remove = delta.get(outerKey).remove(innerKey);
		if (delta.get(outerKey).isEmpty()) {
			delta.remove(outerKey);
		}
		return remove;
	}

	public static <T,S,U,V> V removeKeys(Map<T, Map<S, Map<U,V>>> delta, T outerKey, S middleKey, U innerKey) {
		if (!delta.containsKey(outerKey)) return null;
		final V removeKeys = removeKeys(delta.get(outerKey), middleKey, innerKey);
		if (delta.get(outerKey).isEmpty()) {
			delta.remove(outerKey);
		}
		return removeKeys;
	}
}
