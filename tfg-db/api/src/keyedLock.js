/** Exclusión mutua por clave: las tareas de una misma clave se ejecutan de una en una; claves distintas, en paralelo. */
export class KeyedLock {
  constructor() { this.tails = new Map(); }

  async run(key, fn) {
    const previous = this.tails.get(key) ?? Promise.resolve();
    let release;
    const mine = new Promise((resolve) => { release = resolve; });
    const tail = previous.then(() => mine);
    this.tails.set(key, tail);
    await previous;
    try {
      return await fn();
    } finally {
      release();
      if (this.tails.get(key) === tail) this.tails.delete(key);
    }
  }
}
