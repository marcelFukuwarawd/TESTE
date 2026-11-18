package com.btcswing.simulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.btcswing.simulator.data.db.AppDatabase
import com.btcswing.simulator.data.db.PortfolioEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados básicos (rodam em emulador/aparelho — não no CI,
 * que executa apenas os testes unitários JVM). Validam contexto, banco e
 * preferências no aparelho real.
 */
@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {

    @Test fun contexto_do_app_existe() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.btcswing.simulator.debug", context.packageName)
    }

    @Test fun banco_cria_e_le_portfolio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.get(context)
        db.portfolioDao().upsert(
            PortfolioEntity(id = 1, cashUsdt = 1234.5, initialCapitalUsdt = 1234.5,
                posQtyBtc = null, posEntryPrice = null, posOpenedAt = null, posCostBasis = null,
                posStopLoss = null, posTakeProfit = null, lastTradeTime = null,
                lastPrice = 50_000.0, lastUpdatedAt = System.currentTimeMillis())
        )
        val row = db.portfolioDao().get()
        assertNotNull(row)
        assertEquals(1234.5, row!!.cashUsdt, 1e-9)
    }

    @Test fun strings_principais_existem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.getString(R.string.app_name).isNotBlank())
        assertTrue(context.getString(R.string.widget_label).isNotBlank())
    }
}
