package dev.enchantingoverhaul.core;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BottleAccountingTest {
    private static BottleAccounting.Slot empty() { return new BottleAccounting.Slot(0,64,false,false); }
    @Test void consumedPotionIsReplacedWithExactlyOneBottle() {
        var slots=List.of(new BottleAccounting.Slot(1,1,true,false));
        var plan=BottleAccounting.plan(slots,Map.of(0,1),List.of(0)).orElseThrow();
        assertEquals(List.of(1),plan.consumed()); assertEquals(List.of(1),plan.bottlesAdded());
        assertEquals(1,slots.getFirst().count());
    }
    @Test void stackOfBreathLeavesSurplusAndMergesReturnedBottle() {
        var slots=List.of(new BottleAccounting.Slot(12,64,true,false),new BottleAccounting.Slot(63,64,false,true));
        var plan=BottleAccounting.plan(slots,Map.of(0,1),List.of(0,1)).orElseThrow();
        assertEquals(List.of(1,0),plan.consumed()); assertEquals(List.of(0,1),plan.bottlesAdded());
    }
    @Test void fullReturnSlotsRejectWithoutChangingInput() {
        var slots=List.of(new BottleAccounting.Slot(12,64,true,false),new BottleAccounting.Slot(64,64,false,true));
        assertTrue(BottleAccounting.plan(slots,Map.of(0,1),List.of(0,1)).isEmpty());
        assertEquals(12,slots.getFirst().count()); assertEquals(64,slots.getLast().count());
    }
    @Test void finalBreathCanReuseItsFreedSlotEvenWithFullBottleOutput() {
        var slots=List.of(new BottleAccounting.Slot(1,64,true,false),new BottleAccounting.Slot(64,64,false,true));
        var plan=BottleAccounting.plan(slots,Map.of(0,1),List.of(0,1)).orElseThrow();
        assertEquals(List.of(1,0),plan.bottlesAdded());
    }
    @Test void synthesisReturnsEveryBottleWithoutConsumingSurplusOrUnrelatedStorage() {
        var slots=List.of(new BottleAccounting.Slot(1,1,true,false),new BottleAccounting.Slot(1,1,true,false),
                new BottleAccounting.Slot(10,64,false,false),empty());
        var plan=BottleAccounting.plan(slots,Map.of(0,1,1,1,2,4),List.of(0,1,2,3)).orElseThrow();
        assertEquals(List.of(1,1,4,0),plan.consumed());
        assertEquals(2,plan.bottlesAdded().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0,plan.bottlesAdded().get(2));
    }
    @Test void noPaymentOrNonBottledCatalystNeverGeneratesBottle() {
        var slots=List.of(new BottleAccounting.Slot(8,64,true,false),new BottleAccounting.Slot(1,1,false,false));
        assertEquals(List.of(0,0),BottleAccounting.plan(slots,Map.of(0,0,1,0),List.of(0,1)).orElseThrow().bottlesAdded());
        assertEquals(List.of(0,0),BottleAccounting.plan(slots,Map.of(1,1),List.of(0,1)).orElseThrow().bottlesAdded());
    }
    @Test void forgedPlansAndMissingResourcesFailClosed() {
        var slots=List.of(new BottleAccounting.Slot(1,1,true,false),empty());
        for (var consumption:List.of(Map.of(0,2),Map.of(0,-1),Map.of(-1,1),Map.of(3,1)))
            assertTrue(BottleAccounting.plan(slots,consumption,List.of(0,1)).isEmpty());
        assertTrue(BottleAccounting.plan(slots,Map.of(0,1),List.of(1,1)).isEmpty());
        assertTrue(BottleAccounting.plan(slots,Map.of(0,1),List.of(5)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new BottleAccounting.Slot(2,1,true,false));
    }
    @Test void bottleStackLimitsAndMultipleSlotsConserveCounts() {
        for(int count=1;count<=64;count++) for(int existing=0;existing<=64;existing++) {
            var slots=List.of(new BottleAccounting.Slot(count,64,true,false),new BottleAccounting.Slot(existing,64,false,true),empty());
            var result=BottleAccounting.plan(slots,Map.of(0,count),List.of(0,1,2)).orElseThrow();
            assertEquals(count,result.bottlesAdded().stream().mapToInt(Integer::intValue).sum());
            for(int i=0;i<slots.size();i++) assertTrue(slots.get(i).count()-result.consumed().get(i)+result.bottlesAdded().get(i)<=64);
        }
    }
}
