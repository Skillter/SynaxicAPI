# Synaxic Deployment Scripts

This directory contains scripts for deploying Synaxic across multiple VPS servers with PostgreSQL and Redis replication.

## Quick Reference

### Main VPS Deployment

```bash
# 1. Initial setup
./scripts/setup-main-vps.sh --install

# 2. (Optional) Configure Cloudflare SSL/TLS
# This can also be done during --install by answering the SSL setup prompt
./scripts/setup-main-vps.sh --setup-ssl

# 3. Start services
docker-compose -f docker-compose.prod.yml up --build -d

# 4. Configure replication
./scripts/setup-main-vps.sh --setup-replication

# 5. Add replicas to load balancer
./scripts/setup-main-vps.sh --add-replica <replica-ip>
```

### Replica VPS Deployment

```bash
# 1. Install prerequisites
./scripts/setup-replica-vps.sh --install

# 2. Copy .env and redis/tls/ from main VPS

# 3. Configure replica
./scripts/setup-replica-vps.sh --configure <main-vps-ip>

# 4. Start services
docker-compose -f docker-compose.replica.yml up --build -d

# 5. Check status
./scripts/setup-replica-vps.sh --status
```

## Available Scripts

### setup-main-vps.sh
Manages the main VPS (PostgreSQL master, Redis master, primary app instance).

**Options:**
- `--install` - Initial installation (Docker, configs, .env)
- `--setup-ssl` - Configure Cloudflare SSL/TLS certificates for End-to-End Encryption
- `--setup-replication` - Create PostgreSQL replication user
- `--add-replica <ip>` - Add replica to load balancer
- `--remove-replica <ip>` - Remove replica from load balancer
- `--update` - Interactive replica management

**Safe to re-run:** Yes (idempotent)

### setup-replica-vps.sh
Manages replica VPS servers (PostgreSQL replica, Redis replica, app in read mode).

**Options:**
- `--install` - Install prerequisites
- `--configure <main-ip>` - Configure replica to connect to main VPS
- `--set-main-ip <main-ip>` - Update main VPS IP
- `--status` - Check replication status

**Safe to re-run:** Yes (idempotent)

### setup-master.sh
Legacy script - functionality now integrated into `setup-main-vps.sh --setup-replication`.
Creates the PostgreSQL replication user on the main VPS.

**Usage:** `./scripts/setup-master.sh` (standalone, or use `setup-main-vps.sh --setup-replication`)

### check-replication-status.sh
Check PostgreSQL replication health on master or replica.

**Usage:**
- `./scripts/check-replication-status.sh master` - Check on main VPS
- `./scripts/check-replication-status.sh replica` - Check on replica VPS

**Output:**
- Connected replicas (master)
- Replication lag in seconds (replica)
- LSN positions
- Replication slot status

### init-replica.sh
Automatically initializes PostgreSQL replica using `pg_basebackup`.
Called by Docker on replica container startup.

**Features:**
- Waits for main VPS to be available
- Creates replication slot on master (idempotent)
- Performs base backup from master
- Configures standby mode
- Safe to re-run (checks if already initialized)

**Manual usage:** Not typically run directly (Docker runs automatically)

## Cloudflare SSL/TLS Setup

To enable End-to-End Encryption with Cloudflare (Full Strict mode):

### Prerequisites
1. Domain configured in Cloudflare
2. Cloudflare SSL/TLS mode set to **"Full (Strict)"**

### Step 1: Generate Cloudflare Origin Certificate

1. Go to **Cloudflare Dashboard** → **SSL/TLS** → **Origin Server**
2. Click **"Create Certificate"**
3. Keep default settings (15-year validity, RSA 2048)
4. You'll see two things:
   - **Origin Certificate** (starts with `-----BEGIN CERTIFICATE-----`)
   - **Private Key** (starts with `-----BEGIN PRIVATE KEY-----`)
5. Save both to files on your local machine:
   - `cloudflare-cert.pem` (certificate)
   - `cloudflare-key.pem` (private key)

⚠️ **Important:** Cloudflare only shows the private key once. Copy it immediately!

**Note on Multi-Domain Support:** Cloudflare Origin Certificates support Subject Alternative Names (SANs), meaning you can create a single certificate that covers multiple domains. When creating your certificate in Cloudflare, specify all the domains you need (e.g., `synaxic.skillter.dev, api.synaxic.skillter.dev`). Then when running the setup script, you can list all of them and they'll be automatically configured in Nginx.

### Step 2: Configure SSL on Your VPS

#### Option A: During Initial Setup (Recommended)
```bash
./scripts/setup-main-vps.sh --install
# When prompted "Do you want to set up Cloudflare SSL/TLS now?", answer: y
# Then provide paths to your certificate and private key files
```

#### Option B: Configure SSL Separately
```bash
./scripts/setup-main-vps.sh --setup-ssl
```

The script will prompt you for:
- **Domain name(s)** (can be one or multiple, comma-separated)
  - Single domain: `api.example.com`
  - Multiple domains: `synaxic.skillter.dev, api.synaxic.skillter.dev`
- **Path to Origin Certificate** (PEM format)
- **Path to Private Key** (PEM format)

### What Gets Set Up
- Certificates installed to `/etc/ssl/cloudflare/`
- Nginx configured for:
  - HTTPS on port 443 with SSL/TLS
  - Support for multiple domains (if provided)
  - Automatic HTTP→HTTPS redirect for all domains
  - Proper SSL protocols (TLSv1.2, TLSv1.3)
- Configuration saved to `.env.ssl` for future reference
- Supports wildcard domains via Cloudflare multi-domain certificates

### Verify Setup
After configuration, verify your Nginx config:
```bash
sudo nginx -t
```

Then restart Nginx:
```bash
sudo systemctl restart nginx
```

### Update Certificate
If you need to update certificates (they expire after 15 years):
```bash
./scripts/setup-main-vps.sh --setup-ssl
```

The script is idempotent - it will update existing certificates without losing configuration.

## Script Flow Diagram

```
Main VPS Setup:
  setup-main-vps.sh --install
    ├─> Install Docker & Docker Compose
    ├─> Generate TLS certificates (Redis)
    ├─> Create .env with passwords
    ├─> Generate PostgreSQL master config
    ├─> Generate Redis config
    └─> [Optional] setup-ssl-certificates (prompts for Cloudflare SSL)

  [Optional] setup-main-vps.sh --setup-ssl
    ├─> Install Cloudflare Origin Certificate
    └─> Update Nginx config with SSL

  docker-compose -f docker-compose.prod.yml up -d
    ├─> Start PostgreSQL master
    ├─> Start Redis master
    ├─> Start Spring Boot app
    ├─> Start Prometheus
    └─> Start Grafana

  setup-main-vps.sh --setup-replication
    └─> Create PostgreSQL replication user

Replica VPS Setup:
  setup-replica-vps.sh --install
    ├─> Install Docker & Docker Compose
    └─> Install PostgreSQL client

  [Copy .env and redis/tls/ from main VPS]

  setup-replica-vps.sh --configure <main-ip>
    ├─> Update .env with main VPS IP
    ├─> Generate Redis replica config
    └─> Test connectivity to main VPS

  docker-compose -f docker-compose.replica.yml up -d
    ├─> Start PostgreSQL replica
    │   └─> init-replica.sh runs automatically
    │       ├─> Wait for master
    │       ├─> Create replication slot
    │       ├─> Perform pg_basebackup
    │       └─> Start streaming replication
    ├─> Start Redis replica
    ├─> Start Spring Boot app (read mode)
    ├─> Start Prometheus
    └─> Start Grafana

  setup-replica-vps.sh --status
    ├─> Check PostgreSQL replication status
    └─> Check Redis replication status

Back to Main VPS:
  setup-main-vps.sh --add-replica <replica-ip>
    ├─> Add to Nginx upstream
    ├─> Update Prometheus scrape targets
    └─> Update PostgreSQL pg_hba.conf
```

## Environment Variables

Scripts automatically manage these variables in `.env`:

**Generated by setup-main-vps.sh:**
```bash
POSTGRES_PASSWORD=<random>
REDIS_PASSWORD=<random>
GRAFANA_ADMIN_PASSWORD=<random>
POSTGRES_REPLICATOR_USER=replicator
POSTGRES_REPLICATOR_PASSWORD=<random>
```

**Added by setup-replica-vps.sh:**
```bash
POSTGRES_MASTER_HOST=<main-vps-ip>
REDIS_MASTER_HOST=<main-vps-ip>
```

**User must add manually:**
```bash
GOOGLE_CLIENT_ID=<from-google-console>
GOOGLE_CLIENT_SECRET=<from-google-console>
```

## Configuration Files Generated

### Main VPS
- `redis/redis.conf` - Redis master configuration
- `postgres/master/postgresql.conf` - PostgreSQL master configuration
- `postgres/master/pg_hba.conf` - PostgreSQL authentication
- `nginx/nginx.conf` - Load balancer configuration
- `prometheus.prod.yml` - Prometheus scrape targets
- `redis/tls/redis.{crt,key}` - TLS certificates
- `redis/tls/truststore.p12` - Java truststore

### Replica VPS
- `redis/redis-replica.conf` - Redis replica configuration
- All PostgreSQL configs are pre-provided in repository
- Uses same TLS certificates as main VPS

## Idempotency

All scripts are designed to be run multiple times safely:

✅ Re-running `--install` skips existing installations
✅ Re-running `--setup-replication` updates existing user
✅ Re-running `--configure` updates configs without data loss
✅ `init-replica.sh` detects existing data and skips initialization

## Monitoring Replication

**Check from main VPS:**
```bash
./scripts/check-replication-status.sh master
```

**Check from replica VPS:**
```bash
./scripts/check-replication-status.sh replica
# or
./scripts/setup-replica-vps.sh --status
```

**Key metrics:**
- **lag_seconds < 1**: Excellent
- **lag_seconds < 10**: Good
- **lag_seconds > 30**: Investigate (network, disk I/O, load)

## Troubleshooting

### "Could not connect to master"
- Check firewall allows 5432 and 6380 from replica IP
- Verify replication user exists: `setup-main-vps.sh --setup-replication`
- Test connectivity: `nc -zv <main-ip> 5432`

### "Replica shows as master"
- `standby.signal` missing or data not from pg_basebackup
- Fix: Remove data volume and restart container

### "Replication lag increasing"
- Network bandwidth issue between servers
- High load on main VPS
- Increase `wal_keep_size` in postgresql.conf

### "Permission denied on redis/tls"
- Run: `chmod 644 redis/tls/*` on both servers
- Ensure files are readable by docker user (not root-only)

## Security Notes

⚠️ **Before production:**

1. Change default passwords in `.env`
2. Restrict `pg_hba.conf` to specific IPs (not 0.0.0.0/0)
3. Use proper SSL certificates (not self-signed)
4. Set up VPN between servers
5. Enable firewall rules
6. Rotate credentials regularly

## Documentation

- `DEPLOYMENT_WORKFLOW.md` - Complete deployment guide
- `REPLICATION_SETUP.md` - Detailed replication documentation
- `ARCHITECTURE.md` - System architecture
- `CLAUDE.md` - Development guidelines

## Support

For issues or questions:
1. Check logs: `docker logs <container-name>`
2. Review documentation in root directory
3. Check replication status scripts
4. Verify environment variables in `.env`
