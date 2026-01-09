package com.example.demo.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class KqlService {

	// 따음표 감싸져있는 여부에따라서 문자들을 배열에 넣어줌 그리고 중복방지도 적용함
	public static String divideTokenProcess(String text) throws Exception {
		Map<Integer, String> map = new HashMap<>();
		ArrayList<Integer> indexList = new ArrayList<>();
		int index = 0;
		text = escapeProcess(text);// 따음표 바깥에있는토큰들만 특수문자 처리 괄호도 포함
		text = NearAddQuotes(text);// near 숫자 패턴일때 따음표 추가
		text = text.trim();
		String otext = text; // 오리지널 텍스트
		Map<String, Object> resultMap = dubleBracketProcess(text, map, indexList); // 괄호 토큰은 여기서 해결 (증첩괄호 포함)
		map = (Map<Integer, String>) resultMap.get("map");
		indexList = (ArrayList<Integer>) resultMap.get("indexList");
		text = (String) resultMap.get("text");
		String result = "";
		Pattern pattern = Pattern.compile("\"([^\"]*)\""); // 따움표로 감싸지는경우 |\\([^()]*\\) 지움
		Matcher matcher = pattern.matcher(text);
		while (true) {
			if (matcher.find()) { // 따음표로 감싸져있을때
				String findString = matcher.group(0).trim();// 따음표 감싼문자
				if (map.containsValue(findString)) { // 중복 방지
					Matcher checkd1 = Pattern.compile(findString).matcher(otext);
					int len1 = map.get(index).length();
					int count = index + len1;
					while (checkd1.find(count)) {
						index = checkd1.start();
						break;
					}
				} else {
					index = otext.indexOf(findString);
				}
				indexList.add(index); // 문자조합을 순서대로 재조립하기위해서 만듬
				map.put(index, findString);
				text = text.replaceFirst(Pattern.quote(findString), ""); // 앞쪽부터 순서대로 치환
			} else {
				if (text.contains(" ")) { // 따음표없는곳에 공백이 있을경우
					for (String s : text.split(" ")) {// 공백을 기준으로 배열을 만들고 배열을 String s 에담아서 문자가공
						if (s.isEmpty())
							continue; // 쓸데없는 공백, 따음표가 홀로있을때
						Collections.sort(indexList);
						if (s.equals("\"")) { // 따음표가 감싸져있는것들은 위에서 제거했으므로 이경우 따음표홀로있을 경우를 의미함
							Pattern firstQ = Pattern.compile("^\""); // 첫번째 따음표 홀로있을경우
							Pattern lastQ = Pattern.compile("\"$"); // 마지막 따음표 홀로있을경우
							Matcher firstQM = firstQ.matcher(otext);
							Matcher lastQM = lastQ.matcher(otext);
							if (firstQM.find()) {
								indexList.add(firstQM.start());
								map.put(firstQM.start(), firstQM.group(0).replace("\"", "")); // \\\" -> "" 수정 따음표 홀로있으며
																								// 문자랑 떨어진경우 (맨앞)
							}
							if (lastQM.find()) {
								indexList.add(lastQM.start());
								map.put(lastQM.start(), lastQM.group(0).replace("\"", "")); // \\\" -> "" 수정 따음표 홀로있으며
																							// 문자랑 떨어진경우 (맨뒤)
							}
						} else { // 여기는 따음표로 감싸져있지않은 문자 와 괄호로 감싸져있지않은 문자들
									// 중복문제 해결및 괄호 안에있는것과 따음표안에있는것들제외한 문자들만 처리
							if (!map.containsValue(s)) {
								findSOutsideIndex(text, indexList, map, otext, s);
							} else { // 이미 중복문자를 저장했으므로 continue문으로 올라간다.
								continue;
							}

						}
					}
				} else {
					index = otext.indexOf(text);
					indexList.add(index);
					map.put(index, text);
				}
				text = text.replace(text, ""); // replaceFirst를 하면 안될거같음
			}
			if (text.isEmpty())
				break;
		}
		Collections.sort(indexList); // 순서에 맞게 정렬(오름차순)

		if (!indexList.isEmpty() && !map.isEmpty()) {
			result = KqlOrganize(otext, map, indexList); // 미완성
		}

		return result;
	}

	// 해당 메서드는 원본문자를 넣고 시작과 끝 번지를 정하면 그사이에 해당 타겟문자의 시작번지를 찾는 메서드
	public static int indexofInRange(String otext, String target, int start, int end) {
		int limit = Math.min(end, otext.length() - 1);
		for (int i = start; i < limit - target.length() + 1; i++) {
			if (otext.startsWith(target, i)) {
				return i;
			}
		}
		return -1;
	}

	// 해당 메서드는 위에서 처리한 토큰으로된 것들을 제외한 나머지 문자들을 중복글자가 같은 인덱스에 들어오지않게 처리
	public static void findSOutsideIndex(String text, ArrayList<Integer> indexList, Map<Integer, String> map,
			String otext, String s) {
		List<Integer> Add = new ArrayList<>();
		for (int cnt = 0; cnt < indexList.size(); cnt++) {
			int blockIndex = indexList.get(cnt);
			String block = map.get(blockIndex);
			int start = blockIndex + block.length();
			int end = (cnt == indexList.size() - 1) ? otext.length() : indexList.get(cnt + 1);
			int findIndex = indexofInRange(otext, s, start, end);

			if (findIndex == -1)
				continue; // continue를 해야 나머지 바깥쪽구간들을 찾을수있음

			if (!map.containsKey(findIndex)) {
				Add.add(findIndex);
			}

		}
		for (int In : Add) {
			map.put(In, s);
			s = s.replace("\"", "\\\""); // 홀로 따음표가 있을경우
			indexList.add(In);
		}
		Collections.sort(indexList);

	}

	// near 숫자 패턴에 따음표 붙이는 메서드 ,따음표 혹은 괄호로 묶여져있는 NEAR 숫자 패턴을 제외
	public static String NearAddQuotes(String text) throws Exception {
		Map<Integer, Object> groupList = new HashMap<>();
		boolean inQuotes = false;
		int depth = 0;
		int num = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\"') {
				inQuotes = !inQuotes;
				continue;
			}
			// 괄호관리
			if (!inQuotes) {
				if (c == '(')
					depth++;
				if (c == ')')
					depth--;
			}
			// 괄호 내부이면 스킵
			if (inQuotes || depth > 0)
				continue;

			// 여기서 부터 바깥영역 검사
			String rset = text.substring(i);
			Matcher m = Pattern.compile("(?i)^NEAR\\s*\\d+").matcher(rset);
			if (m.find()) {
				// 이미 따음표로 싸여져있으면 skip
				if (i > 0 && text.charAt(i - 1) == '\"')
					continue;
				String block = m.group();
				int end = i + m.group().length();
				if (end < text.length() && text.charAt(end) == '\"')
					continue;
				groupList.put(num, new int[] { i, end });
				num++;
				i = end - 1;
			}

		}

		if (depth > 0) {
			throw new Exception("괄호가 잘안닫혀있습니다.");
		}
		StringBuffer textbf = new StringBuffer(text);
		if (groupList.size() > 0) { // insert 할때 앞쪽에서부터 넣어버리면 한칸씩밀려서 오류가 발생 뒤에서부터 넣어야 정확히 넣어짐
			for (int i = num - 1; i >= 0; i--) {
				int[] group = (int[]) groupList.get(i);
				textbf.insert(group[1], "\"");
				textbf.insert(group[0], "\"");
			}
		}
		return textbf.toString();
	}

	// 중첩괄호 문제 해결
	public static Map<String, Object> dubleBracketProcess(String text, Map<Integer, String> map,
			ArrayList<Integer> indexList) throws Exception {
		Map<String, Object> resultMap = new HashMap<>();
		Map<Integer, Object> groupList = new HashMap<>();
		text = text.trim();
		String otext = text.trim();
		boolean inQuotes = false;
		int depth = 0;
		int num = 0;
		for (int i = 0; i < otext.length(); i++) {
			char c = otext.charAt(i);
			if (c == '\"') {
				inQuotes = !inQuotes; // true
			}
			if (!inQuotes) { // 따음표 감싸져있는건 제외
				if (c == '(') {
					depth++;
					int open = i; // 첫번째 여는괄호
					for (int i2 = i + 1; i2 < otext.length(); i2++) {
						char c2 = otext.charAt(i2);
						if (c2 == '(') {
							depth++;
							continue;
						}
						if (c2 == ')') {
							depth--;
							// i2 for문으로 들어왔다는건 depth가 최소 1이라는 증거이기때문 해당조건문이 없으면 밑에 groupList에 저장할수가없기때문이다.
							if (depth != 0) {
								continue;
							}
						}
						if (depth == 0) { // depth가 0이 되어야 괄호 묶음이 완성
							groupList.put(num, new int[] { open, i2 }); // open 괄호로 묶은 시작번지 i2는 닫는괄호 번지
							i = i2;
							num++;
							break;
						}
					}

				}
			}
		}

		if (depth > 0) { // depth가 0보다크다는건 아직 괄호가 남아있다는증거
			throw new Exception("괄호가 잘안닫혀있습니다."); // 웹으로 만들때 Exception으로 빠지게만들거임
		}

		if (groupList.size() > 0) { // 여기가 text를 자르는곳 이텍스트로 인해 newProcessQuotes 메서드가 동작함
			for (int i = 0; i < num; i++) {
				int group[] = (int[]) groupList.get(i);
				int start = group[0];
				int end = Math.min(group[1] + 1, otext.length());
				indexList.add(start);
				String bracket = otext.substring(start, end);
				text = text.replaceFirst(Pattern.quote(bracket), "");
				map.put(start, bracket);
			}
		}

		resultMap.put("map", map);
		resultMap.put("indexList", indexList);
		resultMap.put("text", text);
		return resultMap;
	}

	public static String SplitOR(String text) {
		String rtext = text.trim();
		int depth = 0;
		List<Integer> list = new ArrayList<>();
		List<String> parts = new ArrayList<>();
		boolean inQuotes = false;
		for (int i = 0; i < rtext.length(); i++) {
			char c = rtext.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
				continue;
			}

			if (!inQuotes) { // 따음표 없는구간
				if (c == '(') {
					depth++;
					continue;
				}
				if (c == ')') {
					depth--;
					continue;
				}

				if (depth == 0 && c == 'O' && i + 1 < rtext.length() && rtext.charAt(i + 1) == 'R') {
					list.add(i); // 감싸져있지않은 OR의 시작번지
				}

			}
		}
		int prev = 0;
		// 감싸져있지않은 OR 의 시작번지를 이용하여 문자를 자름
		if (list.size() > 0) {
			for (int in : list) {
				parts.add(rtext.substring(prev, in));
				prev = in + 2;
			}
		}
		parts.add(rtext.substring(prev)); // 마지막 남겨진 문자 넣기

		String result = parts.stream().map(part -> {
			part = part.trim();
			if (part.startsWith("(") && part.endsWith(")")) {
				return part;
			} else {
				return "(" + part + ")";
			}
		}).collect(Collectors.joining(" OR ")); // OR로 합침

		return result;
	}

	// KQL 정리메서드
	public static String KqlOrganize(String otext, Map<Integer, String> map, ArrayList<Integer> indexList) {
		StringBuffer result = new StringBuffer();
		String preType = "none"; // none(없음) , word(단어), op(연산자)
		int count = 0;
		String rtext = "";
		for (int i : indexList) {
			count++;
			String token = map.get(i);
			boolean isOP = token.equalsIgnoreCase("AND") || token.equalsIgnoreCase("OR")
					|| token.equalsIgnoreCase("NOT") || token.matches("(?i)\"NEAR\\s*\\d+\"");
			
			boolean inquotes = token.startsWith("\"") && token.endsWith("\"");
			if (!inquotes && !isOP && !token.matches("^\"[^\"]+\"$") && !token.matches("^\\(.*\\)$")) {
				token = "\"" + token + "\"";
			} // 그냥 단어일때
			if (token.matches("^\\(.*\\)$")) { // 토큰이 괄호로 감쌰저있고 그안에 연산자들이 있을때
				token = token.substring(1, token.length() - 1).trim(); // 괄호제거
				// (?패턴1:패턴2) 그룹으로 묵되 그룹번호저장 X
				Matcher wd = Pattern.compile("(?i)(?:\"[^\"]+\"|\\bNEAR\\s*\\d\\b|\\b[^\\s()]+\\b)").matcher(token);
				//Matcher wd = Pattern.compile("(?i)\\b(?:NEAR\\s*\\d|[^\\s()\"]+)\\b").matcher(token);																									
				StringBuffer sbf = new StringBuffer();
				boolean isOR = false;
				while (wd.find()) {
					String word = wd.group();
					if (word.matches("(?i)(AND\\s+NOT|AND|OR|NOT|NEAR\\s*\\d+)")) {
						wd.appendReplacement(sbf, word);
					}else if(word.startsWith("\"") && word.endsWith("\"")) { // 따음표 있으면 그대로적음
						wd.appendReplacement(sbf,word);	
					}else {
						wd.appendReplacement(sbf, "\"" + word + "\"");				
					}
				}
				wd.appendTail(sbf);
				token = "(" + sbf.toString() + ")";
			}
			if (isOP) { // 연산자일때
				if (token.matches("(?i)\"NEAR\\s*\\d+\""))
					token = token.replace("\"", ""); // near 패턴일때 따음표제거
				result.append(preType.equals("op") && token.equalsIgnoreCase("NOT") ? "" : " ") // not 일때 앞에 연산자가 있으면 공백
																								// 하나만씀
						.append(token.toUpperCase()).append(" ");
				preType = "op";
				continue;
			}

			if (preType.equals("word") && count != indexList.size()) { // 일반단어일때
				result.append(" AND ");
			}
			result.append(token);
			preType = "word";
		} // 공백 일때 " AND " 쓰는곳 및 near패턴 따음표제거 일반단어(따음표)가 연속되면 AND 자동삽입 일반단어 따음표씌우는곳

		if (map.containsValue("OR")) {
			rtext = SplitOR(result.toString());
		} else {
			rtext = result.toString();
		}
		return rtext;
	}

	// 따음표 바깥특수문자들은 지우고 따음표안은 특수문자들은 이스케이프처리
	public static String escapeProcess(String input) {
		if (input.isEmpty())
			return "";
		int lastIndex = 0;
		StringBuffer sb = new StringBuffer();
		Pattern pattern = Pattern.compile("\"([^\"]*)\""); // 따음표로 감싸져있는경우
		Matcher matcher = pattern.matcher(input);
		if (!matcher.find())
			return input;
		matcher.reset();
		while (matcher.find()) {
			// 따음표 왼쪽(바깥쪽) 특수문자 처리
			String outside = input.substring(lastIndex, matcher.start());
			outside = outside.replaceAll("([!@#$%^&*_+=\\[\\]{}|;:,.<>/?\\\\-])", "");// () 괄호지움
			sb.append(outside);

			// 따음표 안쪽 특수문자 처리
			String inside = matcher.group(1);
			inside = inside.replaceAll("([!@#$%()^&*_+=\\[\\]{}|;:,.<>/?\\\\-])", "\\\\$1"); // ()유지
			sb.append("\"").append(inside).append("\"");

			lastIndex = matcher.end();
		}

		// 마지막 따음표번지 (바깥쪽) 특수문자 처리
		if (lastIndex < input.length()) {
			String behindQuotesString = input.substring(lastIndex);
			behindQuotesString = behindQuotesString.replaceAll("([!@#$%^&*_+=\\[\\]{}|;:,.<>/?-\\\\])", ""); // () 괄호지움
			sb.append(behindQuotesString);
		}
		return sb.toString();
	}

}
