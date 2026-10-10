package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class WebBillingMigrationTest {
    @Test
    void newBillingChangelogsCreateTablesAndConstraintsOnExistingSchema() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:web_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("create table users (id bigint primary key)");
                statement.execute("create table promo_codes (id bigint primary key)");
            }
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            for (String file : new String[]{"25-revenuecat-subscription-history.yaml",
                    "26-web-billing-ledger.yaml", "27-retain-deleted-account-billing.yaml"}) {
                new Liquibase("db/changelog/changes/" + file,
                        new ClassLoaderResourceAccessor(), database)
                        .update(new Contexts(), new LabelExpression());
            }
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("select count(*) from web_payments")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
            try (var result = connection.getMetaData().getImportedKeys(
                    null, "PUBLIC", "WEB_CHECKOUTS")) {
                while (result.next()) {
                    assertThat(result.getString("PKTABLE_NAME")).isNotEqualTo("USERS");
                }
            }
            // A retained checkout still owns its financial entries after the profile is removed.
            try (var statement = connection.createStatement()) {
                statement.execute("insert into users (id) values (42)");
                statement.execute("""
                        insert into web_checkouts
                        (id,user_id,plan,state,price_id,success_url,cancel_url,discount_percent,
                         acquisition_type,commission_percent,referrer_commission_percent,
                         trial_days,created_at,expires_at,account_deleted_at)
                        values ('retained',42,'YEARLY','COMPLETED','price_year','success','cancel',
                         15,'DIRECT',0,0,7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """);
                statement.execute("""
                        insert into web_payments
                        (id,checkout_id,amount_paid,commission_base,period_start,recurring_period,
                         currency,paid_at,refunded_amount,commission_amount,
                         referrer_commission_amount)
                        values ('in_retained','retained',5100,5100,CURRENT_TIMESTAMP,true,'gbp',
                         CURRENT_TIMESTAMP,1000,1435,615)
                        """);
                statement.execute("""
                        insert into web_payment_refunds (id,invoice_id,amount)
                        values ('ch_retained','in_retained',1000)
                        """);
                statement.execute("delete from users where id=42");
                try (var result = statement.executeQuery("""
                        select p.commission_amount,p.referrer_commission_amount,r.amount
                        from web_payments p join web_payment_refunds r on r.invoice_id=p.id
                        where p.id='in_retained'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(1435);
                    assertThat(result.getLong(2)).isEqualTo(615);
                    assertThat(result.getLong(3)).isEqualTo(1000);
                }
                try (var result = statement.executeQuery(
                        "select user_id,account_deleted_at from web_checkouts "
                                + "where id='retained'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(42);
                    assertThat(result.getTimestamp(2)).isNotNull();
                }
            }
            try (var result = connection.getMetaData().getImportedKeys(
                    null, "PUBLIC", "WEB_PAYMENT_REFUNDS")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("PKTABLE_NAME")).isEqualTo("WEB_PAYMENTS");
            }
        }
    }
}
