# Claude Development Notes

## Production Server Access

### SSH Access to Main VPS
- **Server**: 45.134.48.99
- **User**: main
- **Command**: `ssh main@45.134.48.99`
- **Authentication**: Private key should auto-authenticate

### ⚠️ IMPORTANT - PRODUCTION SERVER SAFETY
This is a **production server**. Follow these rules strictly:

1. **NEVER run destructive commands** - avoid `rm -rf`, `docker rm -f`, etc.
2. **ALWAYS check current directory** before running commands
3. **READ commands carefully** before executing
4. **ASK for confirmation** if unsure about any command
5. **BACKUP configuration files** before making changes
6. **DO NOT restart services unnecessarily** - users depend on them

### Common Troubleshooting Commands

#### Check System Status
```bash
# Check Docker containers
docker ps -a

# Check application logs
docker logs synaxic-app-main --tail 50

# Check system resources
df -h
free -h

# Check application health
curl -f http://localhost:8080/actuator/health
```

#### Check Deployment Issues
```bash
# Check git status and branch
cd ~/SynaxicAPI
git status
git branch

# Check recent deployment attempts
# Look for deploy logs or recent Docker activity
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.CreatedAt}}"

# Manual deployment test
./scripts/deploy-node.sh
```

#### Service Management
```bash
# Restart services (use carefully)
docker-compose -f docker-compose.prod.yml restart

# View all container logs
docker-compose -f docker-compose.prod.yml logs --tail 20
```

### Deployment Workflow Debugging

#### Common Issues and Solutions

1. **Branch Mismatch**: VPS on wrong branch
   - Check: `git branch`
   - Fix: `git checkout production` (script now handles this automatically)

2. **Docker Build Failures**:
   - Check disk space: `df -h`
   - Check Docker logs: `docker build logs` or container logs
   - Verify compose files exist

3. **Application Health Check Failures**:
   - Check if port 8080 is responding: `curl -f http://localhost:8080/actuator/health`
   - Check application startup logs: `docker logs synaxic-app-main`
   - Verify database connection

4. **Configuration Issues**:
   - Check .env files are present
   - Verify configuration backups were restored correctly
   - Check nginx/replica_ips.txt if using replica nodes

### Emergency Procedures

**If deployment fails**:
1. Check container status: `docker ps -a`
2. Verify previous containers are still running
3. Check logs for error messages
4. Do not attempt multiple rapid deployments

**If services are down**:
1. Check system resources: `df -h` and `free -h`
2. Restart with: `docker-compose -f docker-compose.prod.yml up -d`
3. Monitor health: `watch docker ps`

### Contact Information
- Server access issues should be documented in deployment tickets
- Critical issues may require immediate investigation using the commands above