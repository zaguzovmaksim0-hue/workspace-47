import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import test from 'node:test';

const fixture = new URL('./fixtures/autoscript-1.10.1-des-excerpt.js', import.meta.url);
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(fixture, 'utf8') + '\nthis.Cipher = Cipher; this.decipherDES = decipherDES;', context);
const key = '12345678';
const data = (size) => Buffer.from(Array.from({ length: size }, (_, index) => index & 255));
const goldens = new Map([
  [0, '0.PXWVqYv_gJ0='],
  [1, '7.PXWVqYv_gJ09dZWpi_-AnQ=='],
  [7, '1.8dQvLfi63TM9dZWpi_-AnQ=='],
  [8, '0.0ckTTw7CLtw9dZWpi_-AnQ=='],
  [9, '7.0ckTTw7CLtwpZRuSBSBsSz11lamL_4Cd'],
  [255, '1.0ckTTw7CLtxQFSm18xZwPHStG5qoQgJ1IP1vpckNrlMuXf8Lt2bSQqcWxooAJ3rri7R6DPCpYm0LWHkNw_QrjqrXt9ykRPeGp2eDfdjTQRZpZgInuqZlPPbwkGOcuM4Bkryq9vNxa3sM3az8hKIhKt9BwFNnjWpsJ2iDiHM7hGtCF3lOsiVB7LxsywnVHalG3Ruue4qbsi_kNY0EBEPKbYgjcSmy3v8HRtJSNz0QAeiSbnUE3uvCP6lHRrYqPDHQeCIEtahyETlNXFf9JQwX7qJAFnJsBxLDf4_h-Kh381qpb0H__ZE0WenEStX0wqnl5UHvkwWxjrfwcn0lLcAK7T11lamL_4Cd'],
  [256, '0.0ckTTw7CLtxQFSm18xZwPHStG5qoQgJ1IP1vpckNrlMuXf8Lt2bSQqcWxooAJ3rri7R6DPCpYm0LWHkNw_QrjqrXt9ykRPeGp2eDfdjTQRZpZgInuqZlPPbwkGOcuM4Bkryq9vNxa3sM3az8hKIhKt9BwFNnjWpsJ2iDiHM7hGtCF3lOsiVB7LxsywnVHalG3Ruue4qbsi_kNY0EBEPKbYgjcSmy3v8HRtJSNz0QAeiSbnUE3uvCP6lHRrYqPDHQeCIEtahyETlNXFf9JQwX7qJAFnJsBxLDf4_h-Kh381qpb0H__ZE0WenEStX0wqnl5UHvkwWxjrcP4Sfxdu0XSj11lamL_4Cd'],
]);
const decoded = (wire) => Buffer.from(context.decipherDES([wire], key)[0], 'base64');

test('AutoScript decipherDES restores every DES result tail', () => {
  for (const [size, wire] of goldens) {
    if (size === 0) {
      // Upstream Cipher.stringToBase64('') returns 'AA==', its empty-string
      // representation; the ciphertext itself still decrypts to zero bytes.
      assert.equal(context.decipherDES([wire], key)[0], 'AA==');
      continue;
    }
    assert.ok(decoded(wire).equals(data(size)), `size ${size}`);
  }
});

test('AutoScript decipherDES restores one- two- and three-piece wires', () => {
  const intermediate = new Map([
    [7, '1.8dQvLfi63TM='],
    [8, '0.0ckTTw7CLtw='],
  ]);
  for (const sizes of [[9], [8, 9], [7, 8, 9]]) {
    const pieces = sizes.map((size, index) =>
      index === sizes.length - 1 ? goldens.get(size) : intermediate.get(size));
    const output = context.decipherDES(pieces, key).map((value) => Buffer.from(value, 'base64'));
    assert.ok(output.every((buffer, index) => buffer.equals(data(sizes[index]))), `sizes ${sizes}`);
  }
});

test('AutoScript decipherDES restores a SIGN certificate and signature pair', () => {
  const pieces = ['0.0ckTTw7CLtw=', goldens.get(9)];
  const output = context.decipherDES(pieces, key).map((value) => Buffer.from(value, 'base64'));
  assert.ok(output[0].equals(data(8)), 'certificate');
  assert.ok(output[1].equals(data(9)), 'signature');
});

test('a legacy unpadded last piece loses eight bytes in decipherDES', () => {
  const original = data(9);
  const legacyLast = '7.0ckTTw7CLtwpZRuSBSBsSz';
  const output = Buffer.from(context.decipherDES([legacyLast], key)[0], 'base64');
  assert.equal(output.length, original.length - 8);
  assert.ok(output.equals(original.subarray(0, output.length)));
});
