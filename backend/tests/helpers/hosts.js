// Règles "hôtes" où l'on injecte les lectures factices pour la mesure (voir pads.js).
export const H = {
  joinCodesGet: { match: 'match /joinCodes/{code} {', method: 'get' },
  joinCodesCreate: { match: 'match /joinCodes/{code} {', method: 'create' },
  joinCodesDelete: { match: 'match /joinCodes/{code} {', method: 'delete' },
  membershipsCreate: { match: 'match /memberships/{key} {', method: 'create' },
  membershipsDelete: { match: 'match /memberships/{key} {', method: 'delete' },
  membersDelete: { match: 'match /members/{uid} {', method: 'delete' },
  membersCreate: { match: 'match /members/{uid} {', method: 'create' },
  statusCreate: { match: 'match /status/{uid} {', method: 'create' },
  statusUpdate: { match: 'match /status/{uid} {', method: 'update' },
  statusList: { match: 'match /status/{uid} {', method: 'list' },
  statusDelete: { match: 'match /status/{uid} {', method: 'delete' },
  photosDelete: { match: 'match /photos/{uid} {', method: 'delete' },
  placesCreate: { match: 'match /places/{placeId} {', method: 'create' },
  miffsGet: { match: 'match /miffs/{miffId} {', method: 'get' },
};
