package edu.cse454.pharma;

import java.math.BigDecimal;

import edu.cse454.pharma.ShiftHandoverService.Shift;
import edu.cse454.pharma.ShiftHandoverService.Status;
import edu.cse454.pharma.ShiftHandoverService.TenderTotals;

public final class ShiftHandoverDemo {
    private ShiftHandoverDemo() {}

    public static void main(String[] args) {
        var shift = new Shift("SHIFT-01", "cashier-demo", Status.OPEN);
        var repository = new InMemoryShiftRepository(shift, totals("1000", "500"));
        var handover = new ShiftHandoverService(repository);

        System.out.println("Before submission: " + handover.openForm(shift.id(), shift.cashierId()));
        var counted = totals("950", "500");
        System.out.printf("Cashier's count: cash %s EGP | Visa %s EGP%n", counted.cash(), counted.visa());

        var receipt = handover.submit(shift.id(), shift.cashierId(), counted);
        System.out.println("After submission: " + receipt.status());
        System.out.printf("Cash: %s %s EGP%n", receipt.cash().result(), receipt.cash().amount());
        System.out.printf("Visa: %s %s EGP%n", receipt.visa().result(), receipt.visa().amount());
    }

    private static TenderTotals totals(String cash, String visa) {
        return new TenderTotals(new BigDecimal(cash), new BigDecimal(visa));
    }
}
