# Synaxic Multi-Server Deployment Workflow

This guide provides a streamlined workflow for deploying Synaxic across multiple VPS servers with PostgreSQL and Redis replication.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Main VPS                             │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────────┐ │
│  │ PostgreSQL     │  │ Redis Master   │  │ App (Master)  │ │
│  │ Master         │  │                │  │               │ │
│  │ (Writes)       │  │ (Writes)       │  │ (Write Mode)  │ │
│  └───────┬────────┘  └───────┬────────┘  └───────────────┘ │
│          │                   │                               │
└──────────┼───────────────────┼───────────────────────────────┘
           │                   │
           │ WAL Stream        │ Replication
           │                   │
┌──────────┼───────────────────┼───────────────────────────────┐
│          ▼                   ▼                                │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────────┐ │
│  │ PostgreSQL     │  │ Redis Replica  │  │ App (Replica) │ │
│  │ Replica        │  │                │  │               │ │
│  │ (Reads)        │  │ (Reads)        │  │ (Read Mode)   │ │
│  └────────────────┘  └────────────────┘  └───────────────┘ │
│                       Replica VPS                            │
└──────────────────────────────────────────────────────────────┘
```

## Quick Start Guide

### 1. Main VPS Setup

On the **main VPS** (where the master database and Redis will run):

```bash
# Clone repository
git clone <your-repo-url>
cd synaxic

# Run initial setup (installs Docker, generates configs, creates .env)
./scripts/setup-main-vps.sh --install

# Review and update .env file if needed
nano .env

# Start services
docker-compose -f docker-compose.prod.yml up --build -d

# Wait for services to start (check with: docker ps)
sleep 30

# Configure PostgreSQL replication user
./scripts/setup-main-vps.sh --setup-replication
```

**Expected output:**
- Docker and Docker Compose installed
- TLS certificates generated in `redis/tls/`
- `.env` file created with passwords
- PostgreSQL master config in `postgres/master/`
- Services running on ports 8080, 5432, 6380, 9090, 3000
- Replication user `replicator` created in PostgreSQL

### 2. Replica VPS Setup

On the **replica VPS** (read replica for scaling):

```bash
# Clone repository
git clone <your-repo-url>
cd synaxic

# Install prerequisites
./scripts/setup-replica-vps.sh --install
```

**Copy files from main VPS:**
```bash
# On main VPS, create archive
tar czf synaxic-config.tar.gz .env redis/tls/

# Transfer to replica VPS (replace <replica-ip> with actual IP)
scp synaxic-config.tar.gz user@<replica-ip>:/path/to/synaxic/

# On replica VPS, extract
cd /path/to/synaxic
tar xzf synaxic-config.tar.gz
```

**Configure and start replica:**
```bash
# Configure replica (replace <main-vps-ip> with actual IP)
./scripts/setup-replica-vps.sh --configure <main-vps-ip>

# Start replica services
docker-compose -f docker-compose.replica.yml up --build -d

# Check replication status
./scripts/setup-replica-vps.sh --status
```

**Expected output:**
- Redis replica connected to master
- PostgreSQL streaming from master
- Replication lag < 1 second
- App running in read mode

### 3. Add Replica to Load Balancer (Main VPS)

Back on the **main VPS**, register the replica for load balancing:

```bash
# Add replica IP to Nginx upstream
./scripts/setup-main-vps.sh --add-replica <replica-vps-ip>

# Or use interactive mode
./scripts/setup-main-vps.sh --update

# Apply Nginx configuration
sudo cp nginx/nginx.conf /etc/nginx/nginx.conf
sudo systemctl restart nginx
```

### 4. Verify Deployment

**Check replication on main VPS:**
```bash
./scripts/check-replication-status.sh master
```

Expected output:
```
client_addr    | state     | sync_state | sent_lsn  | write_lsn | flush_lsn | replay_lsn
---------------+-----------+------------+-----------+-----------+-----------+------------
192.168.1.100  | streaming | async      | 0/3000000 | 0/3000000 | 0/3000000 | 0/3000000
```

**Check replication on replica VPS:**
```bash
./scripts/check-replication-status.sh replica
```

Expected output:
```
is_replica: YES - This is a replica
lag_seconds: 0.045
```

**Test write/read operations:**

On main VPS (write):
```bash
docker exec synaxic-postgres-prod psql -U synaxic -d synaxic -c "CREATE TABLE test (id serial, data text);"
docker exec synaxic-postgres-prod psql -U synaxic -d synaxic -c "INSERT INTO test (data) VALUES ('Hello from main VPS');"
```

On replica VPS (read):
```bash
# Should see the data within seconds
docker exec synaxic-postgres-replica psql -U synaxic -d synaxic -c "SELECT * FROM test;"

# Try write (should fail - read-only)
docker exec synaxic-postgres-replica psql -U synaxic -d synaxic -c "INSERT INTO test (data) VALUES ('Should fail');"
# Expected: ERROR: cannot execute INSERT in a read-only transaction
```

## Script Reference

### Main VPS Scripts

| Script | Usage | Description |
|--------|-------|-------------|
| `setup-main-vps.sh --install` | First-time setup | Install Docker, generate configs, create .env |
| `setup-main-vps.sh --setup-replication` | After services start | Create PostgreSQL replication user |
| `setup-main-vps.sh --add-replica <ip>` | After replica setup | Add replica to load balancer |
| `setup-main-vps.sh --remove-replica <ip>` | When removing replica | Remove replica from load balancer |
| `setup-main-vps.sh --update` | Interactive | Manage replicas interactively |
| `check-replication-status.sh master` | Anytime | Check replication status on master |

### Replica VPS Scripts

| Script | Usage | Description |
|--------|-------|-------------|
| `setup-replica-vps.sh --install` | First-time setup | Install Docker and dependencies |
| `setup-replica-vps.sh --configure <main-ip>` | After copying .env and TLS | Configure replica to connect to main |
| `setup-replica-vps.sh --set-main-ip <main-ip>` | Change main VPS IP | Update configuration |
| `setup-replica-vps.sh --status` | Anytime | Check replication status |
| `check-replication-status.sh replica` | Anytime | Detailed replication status |

## Configuration Files

### Automatically Generated

These files are created by the setup scripts:

```
Main VPS:
├── .env                          # Environment variables (DO NOT COMMIT)
├── redis/tls/                    # TLS certificates
│   ├── redis.crt
│   ├── redis.key
│   └── truststore.p12
├── postgres/master/
│   ├── postgresql.conf           # Master PostgreSQL config
│   └── pg_hba.conf              # Authentication rules
└── nginx/nginx.conf              # Load balancer config

Replica VPS:
├── .env                          # Copied from main VPS
├── redis/tls/                    # Copied from main VPS
├── redis/redis-replica.conf      # Redis replica config (generated)
└── postgres/replica/
    ├── postgresql.conf           # Replica PostgreSQL config
    └── pg_hba.conf              # Authentication rules
```

### Pre-existing Configuration Files

These are provided in the repository:

```
docker-compose.prod.yml           # Main VPS Docker Compose
docker-compose.replica.yml        # Replica VPS Docker Compose
postgres/master/postgresql.conf   # PostgreSQL master template
postgres/replica/postgresql.conf  # PostgreSQL replica template
redis/redis-replica.conf          # Redis replica template
scripts/init-replica.sh           # PostgreSQL replica initialization
```

## Idempotency & Re-running Scripts

All scripts are **idempotent** and safe to run multiple times:

- **setup-main-vps.sh --install**: Skips existing Docker installation, regenerates configs only if missing
- **setup-main-vps.sh --setup-replication**: Updates replication user password if it exists
- **setup-replica-vps.sh --configure**: Updates existing configs, doesn't break running services
- **init-replica.sh**: Detects existing data directory, skips re-initialization

To force re-initialization:
```bash
# Stop services
docker-compose -f docker-compose.replica.yml down

# Remove data volumes
docker volume rm synaxic_postgres-replica-data synaxic_redis-replica-data

# Restart (will re-initialize)
docker-compose -f docker-compose.replica.yml up -d
```

## Environment Variables

Key variables in `.env`:

```bash
# Database
POSTGRES_USER=synaxic
POSTGRES_PASSWORD=<auto-generated>
POSTGRES_DB=synaxic

# Replication (auto-added by scripts)
POSTGRES_REPLICATOR_USER=replicator
POSTGRES_REPLICATOR_PASSWORD=<auto-generated>
POSTGRES_MASTER_HOST=<main-vps-ip>          # On replica only

# Redis
REDIS_PASSWORD=<auto-generated>
REDIS_MASTER_HOST=<main-vps-ip>             # On replica only

# OAuth (manual - get from Google Cloud Console)
GOOGLE_CLIENT_ID=your-client-id
GOOGLE_CLIENT_SECRET=your-client-secret

# Monitoring
GRAFANA_ADMIN_PASSWORD=<auto-generated>
```

## Troubleshooting

### Replication Not Starting

**Symptom:** Replica can't connect to master

**Check:**
1. Firewall allows PostgreSQL (5432) and Redis (6380) from replica IP
2. Replication user exists on master: `./scripts/setup-main-vps.sh --setup-replication`
3. Network connectivity: `nc -zv <main-ip> 5432`

**Fix:**
```bash
# On main VPS, allow replica IP in firewall
sudo ufw allow from <replica-ip> to any port 5432
sudo ufw allow from <replica-ip> to any port 6380

# Restart replica
docker-compose -f docker-compose.replica.yml restart
```

### High Replication Lag

**Symptom:** `lag_seconds > 10` in status check

**Check:**
1. Network bandwidth between servers
2. Disk I/O on main VPS
3. `wal_keep_size` setting

**Fix:**
```bash
# Increase WAL retention on main VPS
# Edit postgres/master/postgresql.conf
wal_keep_size = 512MB  # Increase from 256MB

# Restart PostgreSQL
docker-compose -f docker-compose.prod.yml restart postgres
```

### Replica Shows as Master

**Symptom:** `is_replica: NO - This is a master!`

**Cause:** `standby.signal` file missing or data directory not initialized from master

**Fix:**
```bash
# Stop replica
docker-compose -f docker-compose.replica.yml down

# Remove data
docker volume rm synaxic_postgres-replica-data

# Restart (will re-initialize from master)
docker-compose -f docker-compose.replica.yml up -d

# Check logs
docker logs -f synaxic-postgres-replica
```

## Security Checklist

Before going to production:

- [ ] Change all auto-generated passwords in `.env`
- [ ] Restrict `pg_hba.conf` to specific replica IPs (not 0.0.0.0/0)
- [ ] Set up VPN or private network between VPS servers
- [ ] Enable SSL for PostgreSQL connections
- [ ] Set up proper TLS certificates (not self-signed) for Redis
- [ ] Configure firewall to only allow necessary ports
- [ ] Set up monitoring alerts for replication lag
- [ ] Enable automated backups on main VPS
- [ ] Document disaster recovery procedures
- [ ] Test failover procedures

## Monitoring

Access monitoring dashboards:

- **Grafana**: `http://<main-vps-ip>:3000` (admin / <GRAFANA_ADMIN_PASSWORD>)
- **Prometheus**: `http://<main-vps-ip>:9090`
- **API Health**: `http://<main-vps-ip>:8080/actuator/health`

Key metrics to monitor:

- PostgreSQL replication lag
- Redis replication offset difference
- Disk space on main VPS (WAL files can accumulate)
- Network bandwidth between servers
- Application request latency

## Maintenance

### Adding More Replicas

Repeat the replica setup process on new servers:

```bash
# On new replica VPS
./scripts/setup-replica-vps.sh --install
# ... copy files, configure, start ...

# On main VPS
./scripts/setup-main-vps.sh --add-replica <new-replica-ip>
```

### Removing Replicas

```bash
# On main VPS
./scripts/setup-main-vps.sh --remove-replica <old-replica-ip>
sudo systemctl restart nginx

# On old replica VPS
docker-compose -f docker-compose.replica.yml down
```

### Updating Application

```bash
# On all servers (main and replicas)
git pull
docker-compose -f docker-compose.prod.yml up --build -d  # or docker-compose.replica.yml
```

## Disaster Recovery

See `REPLICATION_SETUP.md` for detailed failover procedures.

Quick failover (promote replica to master):

```bash
# On replica VPS
docker exec synaxic-postgres-replica pg_ctl promote -D /var/lib/postgresql/data

# Update application configs to point to new master
# Update .env on all servers with new POSTGRES_MASTER_HOST
```

## Additional Resources

- `REPLICATION_SETUP.md` - Detailed replication configuration
- `ARCHITECTURE.md` - System architecture overview
- `CLAUDE.md` - Development guidelines for Claude Code
- Docker Compose files in repository root
