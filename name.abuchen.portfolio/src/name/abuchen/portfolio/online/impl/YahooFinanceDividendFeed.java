package name.abuchen.portfolio.online.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityEvent.DividendEvent;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.DividendFeed;

public class YahooFinanceDividendFeed implements DividendFeed
{
    @SuppressWarnings("unchecked")
    @Override
    public List<DividendEvent> getDividendPayments(Security security) throws IOException
    {   
        long fromDate = LocalDateTime.now().minusYears(2).toEpochSecond(ZoneOffset.UTC);
        long toDate = LocalDateTime.now().plusYears(2).toEpochSecond(ZoneOffset.UTC);
        List<DividendEvent> answer = new ArrayList<>();
        
        String st = "https://query1.finance.yahoo.com/v7/finance/download/"+ security.getTickerSymbol() + "?period1=" + fromDate + "&period2=" + toDate + "&interval=1d&events=div&includeAdjustedClose=true";
        URL stockURL = new URL(st);
        BufferedReader in = new BufferedReader(new InputStreamReader(stockURL.openStream()));
        String s = null;
        while ((s=in.readLine())!=null) 
        {
            System.out.println(s);
            if(!s.contains("Date"))
            {
               String[] tab = s.split(",");
               LocalDate exDate = YahooHelper.fromISODate(tab[0]);
               Double valuePerShare = Double.parseDouble(tab[1]);
               DividendEvent payment = new DividendEvent();

               payment.setDate(exDate); //$NON-NLS-1$
               payment.setPaymentDate(exDate); //$NON-NLS-1$
               
               payment.setAmount(Money.of(security.getCurrencyCode(), //$NON-NLS-1$
                               Values.Amount.factorize(valuePerShare))); //$NON-NLS-1$

               payment.setSource("yahooFinance.com"); //$NON-NLS-1$
               answer.add(payment);
            }
        }
        return answer;
    }

}
