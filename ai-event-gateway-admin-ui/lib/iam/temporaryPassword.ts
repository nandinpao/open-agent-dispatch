export function generateTemporaryPassword(length = 20): string {
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lower = 'abcdefghijkmnopqrstuvwxyz';
  const digits = '23456789';
  const symbols = '!@#$%&*+-=?';
  const pools = [upper, lower, digits, symbols];
  const all = pools.join('');
  const secureIndex = (max: number) => {
    const values = new Uint32Array(1);
    crypto.getRandomValues(values);
    return values[0] % max;
  };
  const chars = pools.map((pool) => pool[secureIndex(pool.length)]);
  while (chars.length < Math.max(14, length)) chars.push(all[secureIndex(all.length)]);
  for (let index = chars.length - 1; index > 0; index -= 1) {
    const swap = secureIndex(index + 1);
    [chars[index], chars[swap]] = [chars[swap], chars[index]];
  }
  return chars.join('');
}
