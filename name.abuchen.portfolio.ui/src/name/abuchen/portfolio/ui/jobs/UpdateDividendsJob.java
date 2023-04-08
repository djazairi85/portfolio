package name.abuchen.portfolio.ui.jobs;

import java.io.Console;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityEvent;
import name.abuchen.portfolio.model.SecurityEvent.DividendEvent;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.DividendFeed;
import name.abuchen.portfolio.online.Factory;
import name.abuchen.portfolio.online.impl.YahooFinanceDividendFeed;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.util.WebAccess.WebAccessException;

public final class UpdateDividendsJob extends AbstractClientJob
{

    private final List<Security> securities;

    public UpdateDividendsJob(Client client)
    {
        this(client, client.getSecurities());
    }

    public UpdateDividendsJob(Client client, Security security)
    {
        this(client, Arrays.asList(security));
    }

    public UpdateDividendsJob(Client client, List<Security> securities)
    {
        super(client, Messages.JobLabelUpdatingDividendEvents);

        this.securities = new ArrayList<>(securities);
    }

    private double getTaxRate(String ticker)
    {
        if(ticker.contains(".SW"))
            return 0.35;
        else if(ticker.contains(".PA"))
            return 0.25;
        else if(ticker.contains(".L") || ticker.contains(".HK"))
            return 0;
        else if(!ticker.contains(".") || ticker.contains(".AS"))
            return 0.15;
        else
            return 0.3;
    }
    
    @Override
    protected IStatus run(IProgressMonitor monitor)
    {
        monitor.beginTask(Messages.JobLabelUpdatingDividendEvents, IProgressMonitor.UNKNOWN);

        DividendFeed feed = Factory.getDividendFeed(YahooFinanceDividendFeed.class);

        boolean isDirty = false;

        for (Security security : securities)
        {
            try
            {
                double taxRate = getTaxRate(security.getTickerSymbol());
                List<DividendEvent> dividends = feed.getDividendPayments(security);
                if (!dividends.isEmpty())
                {
                    List<DividendEvent> current = security.getEvents().stream()
                                    .filter(event -> event.getType() == SecurityEvent.Type.DIVIDEND_PAYMENT)
                                    .map(event -> (DividendEvent) event).collect(Collectors.toList());

                    for (DividendEvent dividendEvent : dividends)
                    {
                        if (current.contains(dividendEvent))
                        {
                            current.remove(dividendEvent);
                        }
                        else
                        {
                            security.addEvent(dividendEvent);
                            isDirty = true;
                        }
                        
                        long sharesAtDate = getClient().getActivePortfolios().get(0).getCurrentShares(dividendEvent.getDate().atStartOfDay(), security);
                        Optional<Account> account = getClient().getActiveAccounts().stream().filter(a -> a.getCurrencyCode() == security.getCurrencyCode() && !a.getName().toLowerCase().contains("crypto")).findFirst();
                        
                        if(account.isPresent())
                        {
                            Optional<AccountTransaction> dividendTransaction = account.get().getTransactions().stream()
                                            .filter(t -> t.getType() == AccountTransaction.Type.DIVIDENDS 
                                            && t.getDateTime().toLocalDate().compareTo(dividendEvent.getDate()) == 0
                                            && t.getOptionalSecurity().isPresent()
                                            && t.getOptionalSecurity().get().getTickerSymbol() == security.getTickerSymbol()).findFirst();
                            
                            long val = dividendEvent.getAmount().multiply(sharesAtDate).getAmount();
     
                            long tax = (long) (val * taxRate);
                            Money calculatedTax = Money.of(security.getCurrencyCode(), val).multiplyAndRound(taxRate);
                            
                            
                            if(dividendTransaction.isEmpty() && sharesAtDate > 0)
                            {
                                int ret = JOptionPane.showConfirmDialog(null, "New dividend found for " + security.getName() + " at date " + dividendEvent.getDate() + " would you like to import it ?");
                                
                                if(ret == JOptionPane.YES_OPTION)
                                {                                
                                    AccountTransaction at = new AccountTransaction(dividendEvent.getDate().atStartOfDay(), security.getCurrencyCode(), val - tax, security, AccountTransaction.Type.DIVIDENDS);
                                    Unit un = new Unit(Unit.Type.TAX, calculatedTax);
                                    at.addUnit(un);
                                    account.get().addTransaction(at);
                                    at.setShares(Values.Share.factorize(sharesAtDate));
                                    //at.setAmount(val);
                                }
                            }/*
                            else if(dividendTransaction.isPresent())
                            {
                                if(dividendTransaction.get().getAmount() != val - tax)
                                {
                                    int ret = JOptionPane.YES_OPTION; //JOptionPane.showConfirmDialog(null, "Dividend value difference for " + security.getName() + " at date " + dividendEvent.getDate() + " Diff: " + Money.of(security.getCurrencyCode(),(val - dividendTransaction.get().getAmount())) + " . Would you like to fix it ?");
                                    
                                    if(ret == JOptionPane.YES_OPTION)
                                    {
                                        dividendTransaction.get().setAmount(val - tax);
                                    }
                                }
                                Optional<Unit> u = dividendTransaction.get().getUnit(Unit.Type.TAX);
                                if(u.isPresent())
                                {
                                    Unit un = u.get();
                                    int compareTxes = un.getAmount().compareTo(calculatedTax);
                                    if(compareTxes != 0)
                                    {
                                        int ret = JOptionPane.YES_OPTION; //JOptionPane.showConfirmDialog(null, "Dividend tax difference for " + security.getName() + " at date " + dividendEvent.getDate() + " Diff: " + Money.of(security.getCurrencyCode(), compareTxes) + " . Would you like to fix it ?");
                                        
                                        if(ret == JOptionPane.YES_OPTION)
                                        {
                                            dividendTransaction.get().removeUnit(un);
                                            Unit newUn = new Unit(Unit.Type.TAX, calculatedTax);
                                            dividendTransaction.get().addUnit(newUn);
                                            long t = 0;
                                        }
                                    }
                                }
                            }*/
                        }
                    }

                    security.removeEventIf(current::contains);
                    isDirty = isDirty || !current.isEmpty();
                }
            }
            catch (IOException e)
            {
                if (e instanceof WebAccessException)
                    PortfolioPlugin.log(e.getMessage());
                else
                    PortfolioPlugin.log(e);
            }
        }

        if (isDirty)
            getClient().markDirty();

        return Status.OK_STATUS;
    }
}
