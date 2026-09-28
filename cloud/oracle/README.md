# KITTY on a free Oracle Cloud VM

This folder gives you a repeatable setup for an Oracle Cloud Always Free ARM VM. The VM runs KITTY's Python gateway and the smaller Qwen 2B GGUF model. Your phone connects over an HTTPS address, so your laptop can stay off.

Oracle's Always Free Ampere A1 allowance is currently up to 2 OCPUs and 12 GB RAM in total. Availability depends on the home region, and Oracle asks for a phone number and payment card when creating the account. A card is used for verification; do not upgrade the account while testing.

## 1. Create the VM

1. Create an Oracle Cloud account and select your home region.
2. Create **Compute → Instances → Create instance**.
3. Choose Ubuntu 24.04, an Ampere A1 shape, **2 OCPUs / 12 GB RAM**, and create/download an SSH key.
4. Add an ingress rule for TCP **22** only. Do not expose ports 8080 or 8765 to the internet.
5. Copy the VM's public IP and connect from Windows PowerShell:

```powershell
ssh ubuntu@YOUR_VM_IP
```

## 2. Install KITTY

On the VM, run:

```bash
git clone https://github.com/psychspy7/KITTY.AI.git /opt/kitty-ai
cd /opt/kitty-ai
sudo bash cloud/oracle/install.sh
```

The script installs Python, compiles `llama-server` for ARM, downloads the locked 2B Q4 model, creates the local database, and installs two systemd services. The model download is large; let it finish.

## 2.5. Enable cloud memory and chat history

Create a Turso database and token from the Turso dashboard or CLI:

```bash
turso db create kitty-memory
turso db show --url kitty-memory
turso db tokens create kitty-memory
```

On the VM, copy the example environment file and fill in the URL and token:

```bash
sudo cp /opt/kitty-ai/cloud/oracle/turso.env.example /etc/kitty/turso.env
sudo nano /etc/kitty/turso.env
sudo chmod 600 /etc/kitty/turso.env
sudo systemctl restart kitty-gateway
sudo /opt/kitty-ai/.venv/bin/python /opt/kitty-ai/server/kitty.py --home /opt/kitty-ai/data doctor
```

The doctor command should end with `database turso-sync`. KITTY keeps a local Turso replica for fast reads and offline-safe writes, then pushes changes to the cloud. The first Turso database uses a separate `kitty.turso.sqlite3` file, so the old laptop-only `kitty.sqlite3` remains recoverable. Existing local history is not silently copied; export it first if you want to migrate it.

After installation, print the pairing token:

```bash
sudo /opt/kitty-ai/.venv/bin/python /opt/kitty-ai/server/kitty.py --home /opt/kitty-ai/data token
```

## 3. Give the phone a trusted HTTPS address

KITTY deliberately rejects plain HTTP internet URLs. The easiest beginner setup is Tailscale:

1. Install Tailscale on the VM and the phone, then sign both into the same account.
2. On the VM run `sudo tailscale serve --bg http://127.0.0.1:8765`.
3. Tailscale shows an HTTPS URL ending in `ts.net`. Paste that URL and the token into KITTY → Settings.

You can also use a domain with Caddy. Copy `Caddyfile.example` to `/etc/caddy/Caddyfile`, replace `kitty.example.com`, point the domain's DNS record at the VM, and allow TCP 443 in the Oracle security list. Caddy obtains and renews the certificate automatically. Keep the KITTY gateway bound to `127.0.0.1`.

Check the services with:

```bash
sudo systemctl status kitty-model kitty-gateway
sudo journalctl -u kitty-model -u kitty-gateway -f
```

If the model is too slow, keep the 2B profile and reduce the gateway context to 4096. The free ARM VM is a convenient always-on test server; a paid GPU VM is the next step if you need fast long answers or several simultaneous users.

## 4. Updating the cloud server

The VM is controlled through the repository. To update it later:

```bash
cd /opt/kitty-ai
sudo git pull --ff-only
sudo systemctl restart kitty-model kitty-gateway
```

The app update button is separate: it checks `release/update.json` and opens the signed Android release. Never replace the Android signing key after you distribute an APK, or Android will reject an in-place update.
