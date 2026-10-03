'use strict';

// The only production persistence implementation: Firebase Cloud Firestore.
class FirestoreRepository {
  constructor(db) { this.db = db; }
  async get(path) { const snap = await this.db.doc(path).get(); return snap.exists ? snap.data() : null; }
  async set(path, value) { await this.db.doc(path).set(value); }
  async mutate(path, change) {
    return this.db.runTransaction(async tx => {
      const ref = this.db.doc(path), snap = await tx.get(ref);
      const result = change(snap.exists ? snap.data() : null);
      if (result.value !== undefined) tx.set(ref, result.value);
      return result.result;
    });
  }
  async list(path, {where, order, limit = 100} = {}) {
    let query = this.db.collection(path);
    if (where) query = query.where(...where);
    if (order) query = query.orderBy(...order);
    const snap = await query.limit(limit).get();
    return snap.docs.map(d => ({...d.data(), id: d.id}));
  }
  async reviewed() {
    const snap = await this.db.collectionGroup('turns').where('exportable', '==', true).limit(100).get();
    return snap.docs.map(d => ({...d.data(), uid: d.ref.parent.parent.id, id: d.id}));
  }
}
module.exports = {FirestoreRepository};
