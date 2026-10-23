"""Verify a reference publication without importing BOM Fidelity or running cases."""
import hashlib
import json
import sys
import tarfile
from pathlib import Path, PurePosixPath


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError('duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique)


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def verify(root):
    manifest = read_json(root/'MANIFEST.json')
    expected = manifest['files']
    actual = {p.relative_to(root).as_posix() for p in root.rglob('*')
              if p.is_file() and '.git' not in p.relative_to(root).parts
              and p.relative_to(root).as_posix() != 'MANIFEST.json'}
    if actual != set(expected):
        raise ValueError('manifest membership mismatch: ' + str(actual ^ set(expected)))
    for name, item in expected.items():
        path = PurePosixPath(name)
        if path.is_absolute() or '..' in path.parts or '\\' in name:
            raise ValueError('unsafe manifest path')
        target = root.joinpath(*path.parts)
        if target.is_symlink() or not target.resolve().is_relative_to(root.resolve()):
            raise ValueError('unsafe filesystem entry')
        raw = target.read_bytes()
        if digest(raw) != item['sha256'] or len(raw) != item['bytes']:
            raise ValueError('hash or length mismatch: ' + name)
    locations = set()
    for index_path in root.glob('cases/*/source-index.json'):
        case = index_path.parent
        index = read_json(index_path)
        sources = {}
        for entry in index['archives']:
            archive = case/entry['file']
            if digest(archive.read_bytes()) != entry['sha256']:
                raise ValueError('archive identity mismatch')
            with tarfile.open(archive, 'r:gz') as tar:
                total = 0
                for member in tar.getmembers():
                    name = PurePosixPath(member.name)
                    if name.is_absolute() or '..' in name.parts or member.issym() or member.islnk():
                        raise ValueError('unsafe source archive member')
                    if member.isfile():
                        total += member.size
                        if member.size > 32_000_000 or total > 200_000_000:
                            raise ValueError('oversized source archive')
                        raw = tar.extractfile(member).read()
                        sources[member.name] = raw
                        # Round-two labels use package@version rather than crate-version.
                        package = entry['package']
                        remainder = '/'.join(name.parts[1:])
                        sources[package+'/'+remainder] = raw
        for path in (case/'source').rglob('*') if (case/'source').exists() else []:
            if path.is_file():
                sources[path.relative_to(case/'source').as_posix()] = path.read_bytes()
        label_sets = [(read_json(p), sources) for p in
                      [case/'labels.json', case/'reference-labels.v1.json'] if p.exists()]
        if (case/'variant-labels.json').exists():
            for name, data in read_json(case/'variant-labels.json').items():
                variant_sources = dict(sources)
                for path in (case/'variants'/name).rglob('*'):
                    if path.is_file():
                        variant_sources[path.relative_to(case/'variants'/name).as_posix()] = path.read_bytes()
                label_sets.append((data, variant_sources))
        for data, selected_sources in label_sets:
            for item in walk(data):
                location = item.get('location', item)
                if 'sourceSha256' not in location or 'line' not in location:
                    continue
                name = location.get('path', location.get('file'))
                raw = selected_sources.get(name)
                if raw is None or digest(raw) != location['sourceSha256']:
                    raise ValueError('unbound source location: ' + str(name))
                lines = raw.decode('utf-8').splitlines()
                line = location['line']
                if not 1 <= line <= len(lines):
                    raise ValueError('invalid line: ' + name)
                if 'column' in location:
                    column = location['column']
                    if not 1 <= column <= len(lines[line-1])+1:
                        raise ValueError('invalid Unicode column: ' + name)
                    if 'offset' in location and location['offset'] != column-1:
                        raise ValueError('offset convention mismatch')
                    if 'anchor' in location and not lines[line-1][column-1:].startswith(location['anchor']):
                        raise ValueError('source anchor mismatch: ' + name)
                locations.add((name, location['sourceSha256'], line, location.get('column'), location.get('anchor')))
    if (root/'fixture-matrix.json').exists():
        matrix = read_json(root/'fixture-matrix.json')['cases']
        assert len(matrix) == 24 and sum(x['schemaValid'] for x in matrix) == 20
        for version, parsed, trips in [('13.2.0', 4, 8), ('13.3.0', 20, 40)]:
            for run in ['library-001', 'library-002']:
                data = read_json(root/'results'/run/version/'results.json')
                assert data['expectationMismatches'] == 0
                assert sum(x['parsed'] for x in data['records']) == parsed
                assert data['roundTrips'] == trips
        for variant, runs, count in [('base', ['001','002'], 4), ('aligned', ['003','004'],20)]:
            for run in runs:
                data = read_json(root/'results'/('http-'+variant+'-'+run)/'results.json')
                assert data['successfulImports'] == count and data['expectationMismatches'] == 0
                assert sum(x.get('storedComponentsWithCryptoProperties',0) for x in data['records']) == 0
    return {'status':'PASS', 'files':len(expected), 'uniqueSourceBindingsChecked':len(locations),
            'scope':'Hashes, source bindings and published result consistency; not a new generator evaluation'}


if __name__ == '__main__':
    try:
        print(json.dumps(verify(Path(sys.argv[1] if len(sys.argv)>1 else '.')), sort_keys=True))
    except (AssertionError, ValueError, OSError, KeyError, tarfile.TarError) as error:
        print('FAIL: ' + str(error), file=sys.stderr)
        sys.exit(1)
