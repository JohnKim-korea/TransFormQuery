package com.example.demo.service;

import java.util.ArrayList;
import java.util.Collection;
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
		text = escapeProcess(text);// 따음표, 괄호 바깥에 있는 특수문자들만 지움
		if(text.isBlank()) {
			throw new IllegalArgumentException("특수문자는 따음표와 괄호안에 작성해주세요 !!");
		}
		text = NearAddQuotes(text);// near 숫자 패턴일때 따음표 추가
		text = text.trim();
		String otext = text; // 오리지널 텍스트
		Map<String, Object> resultMap = dubleBracketProcess(text, map, indexList); // 괄호 토큰은 여기서 해결 (증첩괄호 포함)
		map = (Map<Integer, String>) resultMap.get("map");
		indexList = (ArrayList<Integer>) resultMap.get("indexList");
		text = (String) resultMap.get("text");
		String result = "";
		if (!text.isBlank()) {
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
				} else { //토큰에 감싸여있지 않은 문자들 공백을 기준으로 나눈다.
					if (text.contains(" ")) {
						for (String s : text.split(" ")) {// 공백을 기준으로 배열을 만들고 배열을 String s 에담아서 문자가공
							if (s.isEmpty())
								continue; // 쓸데없는 공백, 따음표가 홀로있을때
							Collections.sort(indexList);
							// 여기는 따음표로 감싸져있지않은 문자 와 괄호로 감싸져있지않은 문자들
							// 중복문제 해결및 괄호 안에있는것과 따음표안에있는것들제외한 문자들만 처리
							if (map.isEmpty()) { // 만일 따음표 및 괄호로 감싸여있지않았을때를 대비함
								index = otext.indexOf(s);
								s = s.replace("\"", "\\\"");
								indexList.add(index);
								map.put(index, s);
							}

							if (!map.containsValue(s) && indexList.size() > 0 && !map.isEmpty()) {
								findSOutsideIndex(text, indexList, map, otext, s);
							} else { // 이미 중복문자를 저장했으므로 continue문으로 올라간다.
								continue;
							}

						}
					} else { // 공백으로 안나뉘어있을경우
						index = otext.indexOf(text);
						indexList.add(index);
						text = text.replace("\"", "\\\""); // 따음표혼자 있을경우
						map.put(index, text);
					}
					text = text.replace(text, ""); // replaceFirst를 하면 안될거같음
				}
				if (text.isEmpty())
					break;
			}
		}
		Collections.sort(indexList); // 순서에 맞게 정렬(오름차순)
		
		WrongTextCheck(indexList,map); // 잘못된 텍스트를 검사함 잘못넣었을 경우 오류로 던짐

		if (!indexList.isEmpty() && !map.isEmpty()) {
			result = KqlOrganize(otext, map, indexList);
		}

		return result;
	}
	
	// 연산자를 잘못쓸경우를 대비하여만듬
	public static void WrongTextCheck(ArrayList<Integer> indexList,Map<Integer, String> map ) {
		boolean hasNearNumer = map.values().stream().anyMatch(val -> val.matches("(?i)NEAR(?!\\s*\\d+)")); 
		//A(?!B) => 지금 위치 뒤에 A가 오면 안 되고, B를 매칭(앞에서 뒤를 봄)
		//A(?<!B) => B 바로 앞에 A가 있으면 안 됨(뒤에서 앞을 봄)
		if(hasNearNumer) {
			throw new IllegalArgumentException("NEAR 연산자는 숫자랑 같이 써야합니다.");
		}
		
		for (int i = 0; i < indexList.size(); i++) {
		    String token = map.get(indexList.get(i));

		    if (token.matches("(?i)\"NEAR\\s*\\d+\"")) {

		        // 맨앞 토큰 체크
		        if (i == 0) {
		            throw new IllegalArgumentException("NEAR 앞에는 반드시 단어가 와야 합니다.");
		        }

		        // 맨뒤 토큰 체크
		        if (i == indexList.size() - 1) {
		            throw new IllegalArgumentException("NEAR 뒤에는 반드시 단어가 와야 합니다.");
		        }

		        String preToken = map.get(indexList.get(i - 1));
		        String nextToken = map.get(indexList.get(i + 1));

		        if (isOperater(preToken) || isOperater(nextToken)) {
		            throw new IllegalArgumentException("NEAR 앞뒤에는 단어만 올 수 있습니다.");
		        }
		    }
		}
		
		for (int i = 0; i < indexList.size() - 1; i++) {
			String pre = map.get(indexList.get(i));
			String next = map.get(indexList.get(i + 1));
			if (pre.equalsIgnoreCase("AND") && next.equalsIgnoreCase("NOT")) { // AND NOT 은 허용
				continue;
			}
			if (isOperater(pre) && isOperater(next)) {
				throw new IllegalArgumentException("연산자가 연속으로 올수 없습니다. " + pre + " " + next);
			}
		}
		
	}
	
	public static boolean isOperater(String token) {
		return token.equalsIgnoreCase("AND") || token.equalsIgnoreCase("OR")
		|| token.equalsIgnoreCase("NOT") ||token.matches("(?i)\"NEAR\\s*\\d+\"");
	}

	// 해당 메서드는 원본문자를 넣고 시작과 끝 번지를 정하면 그사이에 해당 타겟문자의 시작번지를 찾는 메서드
	public static int indexofInRange(String otext, String target, int start, int end) {
		if (target == null || target.isEmpty()) {
			return -1;
		}
		int limit= Math.min(end, otext.length()); // end가 문자끝번지를 넘어가는것을 방지
		int max= limit - target.length();

		for (int i = start; i <= max; i++) { // 해당 max를 <= 이 부등호가 아니면 마지막으로 검사해야할 위치를 놓친다.
			if (otext.startsWith(target, i)) { // max는 찾을려고하는 시작인덱스를 찾기때문에 < 이게아니라 <= 이것을 포함시겨야한다.
				return i;
			}
		}
		return -1;
	}

	/* 해당 메서드는 위에서 시작인덱스(키) 토큰(값)으로 이루어진 Map 과 Map에 담겨진 시작인덱스를 담고있는 (정렬된!!) 정수형 indexList를 이용하여
	 * indexList 와 Map 을 기준으로 타겟문자 s 를 찾는 메서드인다.
	 * 1. 0부터시작하여 마지막 indexList의 최솟값(처음 만들어진 토큰)까지 반복문(while)을 이용하여 타겟문자가 없을때까지 찾느다. 
	 * 2. 정렬된 indexList를 기준으로 for 문을 돌린다음에 위에서 언급했던 반복문을 이용하여 블록간의 사이 사이를 타겟문자가 없을때까지 찾는다.
	 * 3. 마지막으로 indexList 최댓값(마지막토큰의 시작번호)을 이용한 끝번호부터 시작해서 문자마지막 번지까지 반복문을 이용하여 타겟문자가 없을때까지 찾는다.
	 * (While 문을 이용하여 찾는 이유는 이리하면 중복된 타겟문자를 찾을수있기때문이다.)
	 * (While문의 break (findIndex == -1) => 타겟문자가 없을때까지)
	 * */ 
	public static void findSOutsideIndex(String text, ArrayList<Integer> indexList, Map<Integer, String> map,
			String otext, String s) {
		List<Integer> Add = new ArrayList<>();
		int fbsidx = Collections.min(indexList); // 첫시작블록 시작번호 이게 end에 들어갈예정
		int lbsidx = Collections.max(indexList); // 마지막블록 시작번호
		int lbeidx = lbsidx + map.get(lbsidx).length(); // 마지막블록 끝번호 start 에 들어갈예정
		int left = 0;
		int right = 0;
		while (true) { // 첫번째 블록 왼쪽구간에서 타겟 문자열 찾기
			int findIndex = indexofInRange(otext, s, left, fbsidx);
			if (findIndex == -1) {
				break;
			}
			if (!map.containsKey(findIndex)) {
				Add.add(findIndex);
			}
			left = findIndex + s.length();
		}

		for (int i = 0; i < indexList.size(); i++) { // 블록 사이구간에서 타겟문자열찾기
			int blockIndex = indexList.get(i);
			String block = map.get(blockIndex);
			int sideStart = blockIndex + block.length(); // 전블록 끝번호
			int sideEnd = (i == indexList.size() - 1) ? blockIndex + block.length() : indexList.get(i + 1); // 다음 블록 시작전
			while (true) { // 중복문자열 방지
				int findIndex = indexofInRange(otext, s, sideStart, sideEnd);
				if (findIndex == -1) {
					break;
				}

				if (!map.containsKey(findIndex)) {
					Add.add(findIndex);
				}
				sideStart = findIndex + s.length();
			}
		}

		right = lbeidx;
		while (true) { // 마지막 블록 오른쪽구간에서 타겟 문자열 찾기
			int findIndex = indexofInRange(otext, s, right, otext.length());
			if (findIndex == -1) {
				break;
			}
			if (!map.containsKey(findIndex)) {
				Add.add(findIndex);
			}
			right = findIndex + s.length();
		}

		s = s.replace("\"", "\\\""); // 홀로 따음표가 있을경우
		for (int In : Add) {
			map.put(In, s);
			indexList.add(In);
		}
		Collections.sort(indexList);
	}

	// near 숫자 패턴에 따음표 붙이는 메서드 (따음표 혹은 괄호로 묶여져있는 NEAR 숫자 패턴을 제외)
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

	//괄호로 감싸여있는 문자들을 map과 indexList에 담는 역활을 함(이렇게 코딩하면 중첩괄호가있는것도 map에 담겨짐) 
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
			if (c == '\"' &&(i==0 || otext.charAt(i-1) != '\\')) { // 이스케이프 따음표방지
				inQuotes = !inQuotes; // true
			}
			if (!inQuotes) { // 따음표 감싸져있는건 제외
				if(depth == 0 && c == ')') {
					throw new IllegalArgumentException("닫는괄호가 먼저나왔습니다.");
				}
				
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
	
	//해당 메서드는 OR연산자를 기준으로 괄호를 묶는역활을 맡고있음 (따음표와 괄호로 감싸져있는부분을제외)
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

		String result = parts.stream().map(part -> { // .map 각요소를 다른값으로 변환
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
	/* 1. 단어와 단어사이에 공백이 있으면 그 공백을 AND로 채워주고 따음표로 감싸져있는 부분을 제외한 모든 문자들을 따음표로 감싸는 역활을함
	 * 2. 따음표로 감싼 near 숫자패턴을 따음표를 없애고 올바른 NEAR 연산자의 모양을 맞춤
	 * */
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
				Matcher wd = Pattern.compile("(?i)(?:\"[^\"]+\"|\\bNEAR\\s*\\d+\\b|\\b[^\\s()]+\\b)").matcher(token);
				// Matcher wd = Pattern.compile("(?i)\\b(?:NEAR\\s*\\d|[^\\s()\"]+)\\b").matcher(token);
				StringBuffer sbf = new StringBuffer();
				boolean isOR = false;
				while (wd.find()) {
					String word = wd.group();
					if (word.matches("(?i)(AND\\s+NOT|AND|OR|NOT|NEAR\\s*\\d+)")) {
						if (word.matches("(?i)near\\s*\\d+")) {
							word = word.replaceAll("(?i)near\\s*(\\d+)", "NEAR $1");
						}
						wd.appendReplacement(sbf, word.toUpperCase());
					} else if (word.startsWith("\"") && word.endsWith("\"")) { // 따음표 있으면 그대로적음
						wd.appendReplacement(sbf, word);
					} else {
						wd.appendReplacement(sbf, "\"" + word + "\"");
					}
				}
				wd.appendTail(sbf);
				token = "(" + sbf.toString().replaceAll("(?<!\\\\)([!@#$%^&*_+=\\[\\]{}|;:,.<>/?\\\\-])", "\\\\$1") + ")"; // 특수문자
																													// 이스케이프
																													// 처리
			}
			if (isOP) { // 연산자일때
				if (token.matches("(?i)\"NEAR\\s*\\d+\"")) {
					token = token.replace("\"", ""); // near 패턴일때 따음표제거
					token = token.replaceAll("(?i)near\\s*(\\d+)", "NEAR $1");
				}
				result.append(preType.equals("op") && token.equalsIgnoreCase("NOT") ? "" : " ") // not 일때 앞에 연산자가 있으면 공백
																								// 하나만씀
						.append(token.toUpperCase()).append(" ");
				preType = "op";
				continue;
			}

			if (preType.equals("word")) { // 일반단어일때 (이부분)
				result.append(" AND ");
			}
			result.append(token);
			preType = "word";
		} // 공백 일때 " AND " 쓰는곳 및 near패턴 따음표제거 일반단어(따음표)가 연속되면 AND 자동삽입 일반단어 따음표씌우는곳
			// OR 확인
		boolean hasOR = map.values().stream().anyMatch(key -> "OR".equalsIgnoreCase(key));

		if (hasOR) {
			rtext = SplitOR(result.toString());
		} else {
			rtext = result.toString();
		}
		return rtext;
	}

	// 따음표 안의 특수문자는 이스케이프 처리 괄호 안은 논외 그외 바깥은 삭제
	public static String escapeProcess(String input) {
		if (input.isEmpty()) {
			return "";
		}
		StringBuffer result = new StringBuffer();
		int depth = 0;
		boolean inqutoes = false;
		for(int i = 0; i<input.length();i++) {
			char c = input.charAt(i);
			if(c == '"') {
				inqutoes = !inqutoes;
				result.append(c);
				continue;
			}
					
			if(inqutoes) {
				if(isSpecialWord(c)) {
					result.append("\\");
				}
				result.append(c);
				continue;
			}
			
			if (!inqutoes) {
				if (c == '(') {
					depth++;
					result.append(c);
					continue;
				}

				if (c == ')') {
					if(depth == 0) {
						throw new IllegalArgumentException("닫는괄호가 먼저나왔습니다.");
					}
					
					depth--;
					result.append(c);
					continue;
				}
			}
			
			if(!(inqutoes || (depth > 0)) && isSpecialWord(c)) { //따음표 바깥구간들
				continue;
			}
			result.append(c);
			
		}
		if(depth > 0) {
			throw new IllegalArgumentException("괄호가 재대로 안닫혀있습니다.");
		}
		
		return result.toString();
	}
	
	public static boolean isSpecialWord(char c) {
		String specialword = "!@#$%^&*+=[]{}|;:,.<>/?\\-";
		int res = specialword.indexOf(c);
		return res >= 0;
	}

}
