#!/usr/bin/env python3
"""Check resolved Compose port bindings without a daemon or third-party packages."""
import copy
import ipaddress
import json
import os
from pathlib import Path
import shutil
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


def contains_application_port(value):
    """Compose may normalize a published port range as a string."""
    parts = str(value).split('-')
    if not all(part.isdigit() for part in parts):
        return False
    return any(int(parts[0]) <= port <= int(parts[-1]) for port in (8080, 8081, 4180))


def validate(config, tailnet_ip=None):
    """Require API loopback and the expected UI/OAuth bind address."""
    tailnet_range = ipaddress.ip_network('100.64.0.0/10')
    if tailnet_ip is not None and ipaddress.ip_address(tailnet_ip) not in tailnet_range:
        raise ValueError('UI/OAuth bind address must be in the Tailscale IPv4 range')
    services = config['services']
    for name, target in [('invoice-worker-api', 8080), ('invoice-worker-ui', 8081),
                         ('invoice-oauth2-proxy', 4180)]:
        service = services[name]
        if not any(port.get('target') == target for port in service.get('ports', [])):
            raise ValueError(f'{name}: expected application port missing')
    for name, service in services.items():
        if service.get('network_mode') == 'host':
            raise ValueError(f'{name}: host networking bypasses port restrictions')
        for port in service.get('ports', []):
            protected = name in ('invoice-worker-api', 'invoice-worker-ui',
                                 'invoice-oauth2-proxy')
            protected |= contains_application_port(port.get('target'))
            protected |= contains_application_port(port.get('published'))
            tailnet_service = name in ('invoice-worker-ui', 'invoice-oauth2-proxy')
            expected_ip = tailnet_ip if tailnet_service and tailnet_ip else '127.0.0.1'
            if protected and port.get('host_ip') != expected_ip:
                raise ValueError(f'{name}: application port must bind to {expected_ip}')
    uri = services['invoice-worker-ui']['environment']['INVOICE_UI_MANUAL_REVIEW_API_BASE_URI']
    if uri != 'http://invoice-worker-api:8080/':
        raise ValueError('UI must use the internal API address')


def compose_config(api_port='8080', ui_port='8081', tailnet_ip=None):
    # Ignore local .env and environment overrides for reproducible repository tests.
    environment = {'PATH': os.environ['PATH'], 'HOME': os.environ['HOME'],
                   'INVOICE_API_PORT': api_port, 'INVOICE_UI_PORT': ui_port}
    if tailnet_ip:
        environment['INVOICE_UI_BIND_ADDRESS'] = tailnet_ip
        environment['OAUTH2_PROXY_BIND_ADDRESS'] = tailnet_ip
    docker = shutil.which('docker')
    docker_works = docker and subprocess.run(
        [docker, 'compose', 'version'], env=environment,
        capture_output=True, check=False).returncode == 0
    compose_command = [docker, 'compose'] if docker_works else ['docker-compose']
    result = subprocess.run(
        compose_command + ['--env-file', '/dev/null', '-f', str(ROOT / 'compose.yaml'),
         '--profile', 'ui', '--profile', 'public', 'config', '--format', 'json'],
        cwd=ROOT, env=environment, check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


class ComposePortSecurityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = compose_config()

    def test_repository_defaults_are_loopback_only(self):
        validate(self.config)

    def test_custom_host_ports_remain_loopback_only(self):
        validate(compose_config('18080', '18081'))

    def test_explicit_tailnet_binding_applies_only_to_ui_and_oauth(self):
        tailnet_ip = str(ipaddress.ip_network('100.64.0.0/10').network_address + 42)
        validate(compose_config(tailnet_ip=tailnet_ip), tailnet_ip=tailnet_ip)

    def test_unsafe_bindings_are_rejected_for_all_three_services(self):
        for service in ('invoice-worker-api', 'invoice-worker-ui',
                        'invoice-oauth2-proxy'):
            for address in (None, '', '0.0.0.0', '::', '::1', '192.0.2.10'):
                with self.subTest(service=service, address=address):
                    config = copy.deepcopy(self.config)
                    port = config['services'][service]['ports'][0]
                    port.pop('host_ip', None)
                    if address is not None:
                        port['host_ip'] = address
                    with self.assertRaises(ValueError):
                        validate(config)

    def test_extra_public_mapping_is_rejected(self):
        for port in ({'target': 8080, 'published': '18080'},
                     {'target': 8081, 'published': '18081'},
                     {'target': 9000, 'published': '8080'},
                     {'target': 9000, 'published': '8081'},
                     {'target': 4180, 'published': '14180'},
                     {'target': 9000, 'published': '4180'},
                     {'target': 9000, 'published': '8000-9000'}):
            with self.subTest(port=port):
                config = copy.deepcopy(self.config)
                config['services']['extra'] = {'ports': [port]}
                with self.assertRaises(ValueError):
                    validate(config)

    def test_host_networking_is_rejected(self):
        config = copy.deepcopy(self.config)
        config['services']['invoice-worker-api']['network_mode'] = 'host'
        with self.assertRaises(ValueError):
            validate(config)

    def test_missing_mapping_is_rejected(self):
        config = copy.deepcopy(self.config)
        config['services']['invoice-worker-ui']['ports'] = []
        with self.assertRaises(ValueError):
            validate(config)

    def test_external_api_address_is_rejected(self):
        config = copy.deepcopy(self.config)
        config['services']['invoice-worker-ui']['environment'][
            'INVOICE_UI_MANUAL_REVIEW_API_BASE_URI'] = 'http://192.0.2.10:8080/'
        with self.assertRaises(ValueError):
            validate(config)


if __name__ == '__main__':
    unittest.main()
