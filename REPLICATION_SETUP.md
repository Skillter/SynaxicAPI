# PostgreSQL & Redis Replication Setup Guide

This guide explains how to set up streaming replication for PostgreSQL and Redis across the main and replica VPS servers.

## Architecture Overview

```
Main VPS:
├── PostgreSQL Master (port 5432) → writes
├── Redis Master (port 6380) → writes
└── App instances → write mode

Replica VPS:
├── PostgreSQL Replica (port 5432) → reads (streams from master)
├── Redis Replica (port 6380) → reads (replicates from master)
└── App instances → read mode
```

## Prerequisites

1. Both VPS servers must be able to reach each other over the network
2. PostgreSQL port 5432 and Redis port 6380 must be open between servers
3. TLS certificates for Redis already generated (see `redis/tls/`)
4. `.env` file configured on both servers

## Environment Variables

Add these to your `.env` file:

### Master VPS (.env)
```bash
# Database
POSTGRES_USER=synaxic
POSTGRES_PASSWORD=<secure-password>
POSTGRES_DB=synaxic

# Replication user
POSTGRES_REPLICATOR_USER=replicator
POSTGRES_REPLICATOR_PASSWORD=<secure-replicator-password>

# Redis
REDIS_PASSWORD=<secure-redis-password>
```

### Replica VPS (.env)
```bash
# Database
POSTGRES_USER=synaxic
POSTGRES_PASSWORD=<secure-password>
POSTGRES_DB=synaxic

# Replication
POSTGRES_MASTER_HOST=<master-vps-ip-or-hostname>
POSTGRES_MASTER_PORT=5432
POSTGRES_REPLICATOR_USER=replicator
POSTGRES_REPLICATOR_PASSWORD=<secure-replicator-password>

# Redis
REDIS_PASSWORD=<secure-redis-password>
REDIS_MASTER_HOST=<master-vps-ip-or-hostname>
```

## Step-by-Step Setup

### 1. Setup Master VPS

```bash
# Start the master services
docker-compose -f docker-compose.prod.yml up -d

# Create replication user and configure master
./scripts/setup-master.sh

# Verify master is running
docker ps
docker exec synaxic-postgres-prod psql -U synaxic -c "SELECT version();"
```

### 2. Configure Redis Replica Config

On the **replica VPS**, update `redis/redis-replica.conf`:

```bash
# Replace placeholders with actual values
sed -i "s/REPLACE_WITH_REDIS_PASSWORD/${REDIS_PASSWORD}/g" redis/redis-replica.conf
sed -i "s/REPLACE_WITH_MASTER_HOST/${REDIS_MASTER_HOST}/g" redis/redis-replica.conf
```

Or manually edit the file and replace:
- `REPLACE_WITH_REDIS_PASSWORD` → Your Redis password
- `REPLACE_WITH_MASTER_HOST` → Master VPS IP/hostname

### 3. Setup Replica VPS

```bash
# Start the replica services
docker-compose -f docker-compose.replica.yml up -d

# The init-replica.sh script will automatically:
# - Wait for master to be available
# - Create replication slot on master
# - Perform pg_basebackup from master
# - Configure standby mode

# Check logs
docker logs -f synaxic-postgres-replica
```

### 4. Verify Replication

On **Master VPS**:
```bash
./scripts/check-replication-status.sh master
```

Expected output should show:
- Connected replicas with `state=streaming`
- Replication slot `replica1_slot` as `active=true`

On **Replica VPS**:
```bash
./scripts/check-replication-status.sh replica
```

Expected output should show:
- `is_replica=true`
- LSN positions advancing
- Low replication lag (< 1 second)

### 5. Test Replication

On **Master VPS**:
```bash
# Write data
docker exec synaxic-postgres-prod psql -U synaxic -d synaxic -c "CREATE TABLE test_replication (id serial, data text);"
docker exec synaxic-postgres-prod psql -U synaxic -d synaxic -c "INSERT INTO test_replication (data) VALUES ('Test data');"
```

On **Replica VPS** (after a few seconds):
```bash
# Read replicated data
docker exec synaxic-postgres-replica psql -U synaxic -d synaxic -c "SELECT * FROM test_replication;"

# Try write (should fail - replica is read-only)
docker exec synaxic-postgres-replica psql -U synaxic -d synaxic -c "INSERT INTO test_replication (data) VALUES ('Should fail');"
# Expected: ERROR: cannot execute INSERT in a read-only transaction
```

### 6. Test Redis Replication

On **Master VPS**:
```bash
docker exec synaxic-redis-prod redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /usr/local/etc/redis/tls/redis.crt SET test_key "test_value"
```

On **Replica VPS**:
```bash
docker exec synaxic-redis-replica redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /usr/local/etc/redis/tls/redis.crt GET test_key
# Expected: "test_value"

# Check replication info
docker exec synaxic-redis-replica redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /usr/local/etc/redis/tls/redis.crt INFO replication
```

## Monitoring Replication Health

### PostgreSQL Replication Lag
```sql
-- On master: check lag for each replica
SELECT
    client_addr,
    state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    sync_state,
    (sent_lsn::bigint - replay_lsn::bigint) AS lag_bytes
FROM pg_stat_replication;

-- On replica: check lag in seconds
SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) AS lag_seconds;
```

### Redis Replication Lag
```bash
# On replica
redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /path/to/redis.crt INFO replication | grep master_repl_offset
redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /path/to/redis.crt INFO replication | grep slave_repl_offset
```

## Failover Procedures

### Promote Replica to Master (PostgreSQL)

If the master fails, promote the replica:

```bash
# On replica VPS
touch /tmp/promote_to_master

# Or use pg_ctl
docker exec synaxic-postgres-replica pg_ctl promote -D /var/lib/postgresql/data

# Update application configuration to point to new master
# Update docker-compose to remove replication settings
```

### Promote Redis Replica

```bash
# On replica
docker exec synaxic-redis-replica redis-cli -a "${REDIS_PASSWORD}" -p 6380 --tls --cacert /usr/local/etc/redis/tls/redis.crt REPLICAOF NO ONE
```

## Troubleshooting

### Replication Not Starting

1. Check network connectivity:
```bash
# From replica, test connection to master
nc -zv <master-ip> 5432
nc -zv <master-ip> 6380
```

2. Check firewall rules on master
3. Verify replication user credentials
4. Check PostgreSQL logs: `docker logs synaxic-postgres-replica`

### Replication Lag Increasing

1. Check disk I/O on master
2. Check network bandwidth between servers
3. Consider increasing `wal_keep_size` on master
4. Monitor with: `./scripts/check-replication-status.sh`

### Replica Slot Inactive

If replication slot becomes inactive:
```sql
-- On master: check slots
SELECT * FROM pg_replication_slots;

-- If slot exists but inactive, drop and recreate on replica restart
SELECT pg_drop_replication_slot('replica1_slot');
```

## Configuration Files Reference

- `postgres/master/postgresql.conf` - Master PostgreSQL config
- `postgres/master/pg_hba.conf` - Master authentication config
- `postgres/replica/postgresql.conf` - Replica PostgreSQL config
- `postgres/replica/pg_hba.conf` - Replica authentication config
- `redis/redis.conf` - Master Redis config
- `redis/redis-replica.conf` - Replica Redis config
- `docker-compose.prod.yml` - Master VPS deployment
- `docker-compose.replica.yml` - Replica VPS deployment

## Security Best Practices

1. **Network Security**:
   - Use VPN or private network between VPS servers
   - Restrict pg_hba.conf to specific IP addresses (not 0.0.0.0/0)
   - Use TLS for all connections

2. **Credentials**:
   - Use strong passwords for replication users
   - Store credentials in `.env` files (never commit)
   - Rotate passwords regularly

3. **Monitoring**:
   - Set up alerts for replication lag > 30 seconds
   - Monitor disk space (WAL files can accumulate)
   - Use Prometheus + Grafana for metrics

## Application Configuration

The Spring Boot application automatically handles read/write splitting based on environment variables:

- `SYNAXIC_REDIS_READ_MODE=MASTER` - Write to master, read from master
- `SYNAXIC_REDIS_READ_MODE=REPLICA` - Write to master, read from replica

For PostgreSQL, the application uses a single datasource URL. To enable read replicas, you would need to configure Spring's `AbstractRoutingDataSource` (not implemented yet).

## Next Steps

- [ ] Set up automated failover with Patroni (PostgreSQL HA)
- [ ] Implement read/write splitting in application layer
- [ ] Configure Redis Sentinel for automatic failover
- [ ] Set up monitoring alerts for replication lag
- [ ] Document backup and recovery procedures
