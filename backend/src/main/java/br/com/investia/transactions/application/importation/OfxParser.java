package br.com.investia.transactions.application.importation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser OFX 2.x (SGML, formato dos bancos brasileiros).
 * Extrai STMTTRN: DTPOSTED, TRNAMT, MEMO/NAME.
 */
public class OfxParser {

    public record OfxTransaction(LocalDate date, BigDecimal amount, String memo) {
    }

    private static final Pattern TRN_BLOCK =
            Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern DTPOSTED =
            Pattern.compile("<DTPOSTED>\\s*([0-9]{8})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRNAMT =
            Pattern.compile("<TRNAMT>\\s*(-?[0-9.,-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMO =
            Pattern.compile("<(?:MEMO|NAME)>\\s*([^<\\r\\n]+)", Pattern.CASE_INSENSITIVE);

    public List<OfxTransaction> parse(String content) {
        List<OfxTransaction> result = new ArrayList<>();
        Matcher block = TRN_BLOCK.matcher(content);
        while (block.find()) {
            String trn = block.group(1);
            Matcher d = DTPOSTED.matcher(trn);
            Matcher a = TRNAMT.matcher(trn);
            Matcher m = MEMO.matcher(trn);
            if (d.find() && a.find()) {
                LocalDate date = LocalDate.parse(d.group(1), DateTimeFormatter.BASIC_ISO_DATE);
                String amountRaw = a.group(1).replace(',', '.');
                result.add(new OfxTransaction(date, new BigDecimal(amountRaw),
                        m.find() ? m.group(1).trim() : "Importado OFX"));
            }
        }
        return result;
    }
}
