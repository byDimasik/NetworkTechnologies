package ru.nsu.fit.g15205.shishlyannikov;

public class Alphabet {
    private static String alphabet = "ACGT";

    private static int countChars(String string, char ch) {
        int count = 0;
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    /***
     * Возврат первого символа алфавита
     */
    public static String getFirstSymb() {
        return "" + alphabet.charAt(0);
    }


    /***
     * Вернуть следующее слово в соответствии с алфавитом
     * @param string - текущее слово
     * @return - следующее слово
     */
    public static String getNextWord(String string) {
        if (string.length() == 0) {
            return "" + alphabet.charAt(0);
        }

        StringBuilder stringBuilder = new StringBuilder(string);

        // если входное слово полностью сосотоит из последних букв алфавита, возвращаем слово из первых букв
        // с увеличенной на единицу длиной
        if (countChars(string, alphabet.charAt(alphabet.length()-1)) == string.length()) {
            stringBuilder = new StringBuilder();
            for (int i = 0; i < string.length()+1; i++) {
                stringBuilder.append(alphabet.charAt(0));
            }
            return stringBuilder.toString();
        }

        // иначе с конца заменяем последние буквы на первые, когда последние кончились, инкрементим букву по алфавиту
        for (int i = string.length() - 1; i > -1; i--) {
            if (string.charAt(i) == alphabet.charAt(alphabet.length() - 1)) {
                stringBuilder.setCharAt(i, alphabet.charAt(0));
            } else {
                int cur_index = alphabet.indexOf(string.charAt(i));
                stringBuilder.setCharAt(i, alphabet.charAt(++cur_index));
                return stringBuilder.toString();
            }
        }

        return null;
    }
}
