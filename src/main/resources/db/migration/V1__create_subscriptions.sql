CREATE TABLE subscriptions (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id              UUID        NOT NULL UNIQUE,
    stripe_customer_id      VARCHAR(64) NOT NULL,
    stripe_subscription_id  VARCHAR(64) NOT NULL UNIQUE,
    status                  VARCHAR(16) NOT NULL,
    current_period_end      TIMESTAMP,
    created_at              TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_company_id              ON subscriptions (company_id);
CREATE INDEX idx_subscriptions_stripe_subscription_id  ON subscriptions (stripe_subscription_id);
